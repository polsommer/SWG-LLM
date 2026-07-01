from __future__ import annotations

import json
import math
import re
import threading
from dataclasses import dataclass
from datetime import datetime, UTC
from pathlib import Path

from .storage import PROJECT_INDEX_DIR, PROJECT_ROOTS, load_json_list, read_text_file, save_json_list


MANIFEST_FILE = PROJECT_INDEX_DIR / "manifest.json"
CHUNKS_FILE = PROJECT_INDEX_DIR / "chunks.json"
GRAPH_FILE = PROJECT_INDEX_DIR / "graph.json"
SEMANTIC_FILE = PROJECT_INDEX_DIR / "semantic.json"
STATUS_FILE = PROJECT_INDEX_DIR / "status.json"

MAX_TEXT_CHARS_PER_FILE = 24000
MAX_CHUNKS_PER_FILE = 4
MAX_SYMBOLS_PER_FILE = 20
MAX_IMPORTS_PER_FILE = 20
MAX_INHERITS_PER_FILE = 12
MAX_FUNCTIONS_PER_FILE = 20
MAX_CALLS_PER_FILE = 20
FAST_INDEX_FILE_THRESHOLD = 2500
FAST_INDEX_TEXT_CHARS_PER_FILE = 6000
FAST_INDEX_CHUNK_CHARS = 1200
FAST_INDEX_CHUNKS_PER_FILE = 1

CODE_EXTENSIONS = {
    ".c",
    ".cc",
    ".cpp",
    ".cxx",
    ".h",
    ".hpp",
    ".hh",
    ".java",
    ".py",
    ".js",
    ".ts",
    ".cs",
    ".lua",
}


@dataclass
class Chunk:
    path: str
    chunk_id: str
    text: str
    symbols: list[str]
    imports: list[str]
    inherits: list[str]
    calls: list[str]


class ProjectIndexer:
    def __init__(self) -> None:
        self.project_roots = PROJECT_ROOTS
        self._lock = threading.Lock()

    def _iter_project_files(self) -> list[Path]:
        files: list[Path] = []
        for root in self.project_roots:
            if not root.exists():
                continue
            for path in root.rglob("*"):
                if path.is_file() and path.suffix.lower() in CODE_EXTENSIONS:
                    files.append(path)
        return sorted(files)

    def _project_relative(self, path: Path) -> str:
        return path.relative_to(path.parents[2]).as_posix()

    def build_file_signature(self) -> list[dict]:
        signature: list[dict] = []
        for path in self._iter_project_files():
            try:
                stat = path.stat()
            except OSError:
                continue
            signature.append(
                {
                    "path": self._project_relative(path),
                    "size": stat.st_size,
                    "mtime": stat.st_mtime,
                }
            )
        return signature

    def _resolve_project_path(self, relative_path: str) -> Path:
        normalized = relative_path.strip().replace("\\", "/")
        for root in self.project_roots:
            candidates = []
            if normalized.startswith("swg-main/"):
                candidates.append((root.parent.parent / normalized).resolve())
            candidates.append((root / normalized).resolve())

            for candidate in candidates:
                allowed_root = root.parent.resolve()
                if str(candidate).startswith(str(allowed_root)) and candidate.exists() and candidate.is_file():
                    return candidate
        raise ValueError(f"Project file not found: {relative_path}")

    def _extract_symbols(self, text: str) -> list[str]:
        patterns = [
            r"\bclass\s+([A-Za-z_][A-Za-z0-9_]*)",
            r"\bstruct\s+([A-Za-z_][A-Za-z0-9_]*)",
            r"\benum\s+([A-Za-z_][A-Za-z0-9_]*)",
            r"\bdef\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(",
            r"\bfunction\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(",
            r"\b([A-Za-z_][A-Za-z0-9_:<>~]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\([^;]{0,120}\)\s*\{?",
        ]
        found: list[str] = []
        for pattern in patterns:
            for match in re.finditer(pattern, text):
                if match.lastindex:
                    found.append(match.group(match.lastindex))
        seen: set[str] = set()
        unique: list[str] = []
        for symbol in found:
            if symbol not in seen:
                seen.add(symbol)
                unique.append(symbol)
        return unique[:80]

    def _extract_imports(self, text: str) -> list[str]:
        patterns = [
            r'^\s*#include\s*[<"]([^>"]+)[>"]',
            r'^\s*import\s+([A-Za-z0-9_., ]+)',
            r'^\s*from\s+([A-Za-z0-9_.]+)\s+import\b',
            r'^\s*using\s+([A-Za-z0-9_.:]+)\s*;',
            r'^\s*require\s*\(\s*[\'"]([^\'"]+)[\'"]\s*\)',
        ]
        found: list[str] = []
        for pattern in patterns:
            for match in re.finditer(pattern, text, re.MULTILINE):
                value = match.group(1).strip()
                if value:
                    found.append(value)
        return self._unique_list(found, limit=40)

    def _extract_inheritance(self, text: str) -> list[str]:
        patterns = [
            r"\bclass\s+[A-Za-z_][A-Za-z0-9_]*\s*:\s*public\s+([A-Za-z_][A-Za-z0-9_:<>]*)",
            r"\bclass\s+[A-Za-z_][A-Za-z0-9_]*\s*\(\s*([A-Za-z_][A-Za-z0-9_., ]+)\s*\)\s*:",
            r"\binterface\s+[A-Za-z_][A-Za-z0-9_]*\s+extends\s+([A-Za-z_][A-Za-z0-9_., ]+)",
            r"\bclass\s+[A-Za-z_][A-Za-z0-9_]*\s+extends\s+([A-Za-z_][A-Za-z0-9_., ]+)",
        ]
        found: list[str] = []
        for pattern in patterns:
            for match in re.finditer(pattern, text):
                value = match.group(1).strip()
                if value:
                    parts = [part.strip() for part in re.split(r"[, ]+", value) if part.strip()]
                    found.extend(parts)
        return self._unique_list(found, limit=24)

    def _extract_function_signatures(self, text: str) -> list[str]:
        patterns = [
            r"^\s*def\s+([A-Za-z_][A-Za-z0-9_]*)\s*\((.*?)\)\s*:",
            r"^\s*function\s+([A-Za-z_][A-Za-z0-9_]*)\s*\((.*?)\)",
            r"^\s*(?:public|private|protected|static|\s)*\s*([A-Za-z_][A-Za-z0-9_]*)\s*\((.*?)\)\s*\{",
            r"^\s*[A-Za-z_][A-Za-z0-9_:<>~*&\s]+\s+([A-Za-z_][A-Za-z0-9_]*)\s*\((.*?)\)\s*(?:const)?\s*\{",
        ]
        found: list[str] = []
        for pattern in patterns:
            for match in re.finditer(pattern, text, re.MULTILINE):
                name = match.group(1).strip()
                args = re.sub(r"\s+", " ", (match.group(2) or "").strip())
                signature = f"{name}({args[:80]})" if args else f"{name}()"
                found.append(signature)
        return self._unique_list(found, limit=60)

    def _extract_calls(self, text: str) -> list[str]:
        found = re.findall(r"\b([A-Za-z_][A-Za-z0-9_:]*)\s*\(", text)
        excluded = {
            "if",
            "for",
            "while",
            "switch",
            "return",
            "sizeof",
            "catch",
            "def",
            "class",
            "function",
            "print",
        }
        filtered = [value for value in found if value not in excluded and not value[0].isdigit()]
        return self._unique_list(filtered, limit=80)

    def _extract_symbol_kinds(self, text: str) -> dict[str, list[str]]:
        kinds = {
            "classes": re.findall(r"\bclass\s+([A-Za-z_][A-Za-z0-9_]*)", text),
            "structs": re.findall(r"\bstruct\s+([A-Za-z_][A-Za-z0-9_]*)", text),
            "enums": re.findall(r"\benum\s+([A-Za-z_][A-Za-z0-9_]*)", text),
            "functions": [sig.split("(", 1)[0] for sig in self._extract_function_signatures(text)],
        }
        return {key: self._unique_list(values, limit=40) for key, values in kinds.items()}

    def _unique_list(self, values: list[str], limit: int) -> list[str]:
        seen: set[str] = set()
        unique: list[str] = []
        for value in values:
            cleaned = value.strip()
            if not cleaned or cleaned in seen:
                continue
            seen.add(cleaned)
            unique.append(cleaned)
            if len(unique) >= limit:
                break
        return unique

    def _tokenize_semantic(self, text: str) -> list[str]:
        lowered = text.lower()
        words = re.findall(r"[a-z_][a-z0-9_]{1,31}", lowered)
        split_words: list[str] = []
        for word in words:
            split_words.append(word)
            split_words.extend(part.lower() for part in re.findall(r"[A-Z]?[a-z]+|[A-Z]+(?![a-z])|\d+", word) if part)
        filtered = [
            token
            for token in split_words
            if len(token) >= 2 and token not in {
                "the", "and", "for", "with", "from", "that", "this", "void", "true", "false",
                "const", "class", "struct", "return", "public", "private", "protected", "static",
                "include", "using", "import", "auto", "bool", "int", "float", "double", "char",
            }
        ]
        return filtered[:4000]

    def _build_semantic_index(self, chunks: list[dict]) -> dict:
        doc_freq: dict[str, int] = {}
        chunk_terms: list[tuple[dict, dict[str, int]]] = []

        for chunk in chunks:
            text = " ".join(
                [
                    str(chunk.get("path", "")),
                    str(chunk.get("text", "")),
                    " ".join(chunk.get("symbols", [])),
                    " ".join(chunk.get("imports", [])),
                    " ".join(chunk.get("inherits", [])),
                    " ".join(chunk.get("calls", [])),
                ]
            )
            tokens = self._tokenize_semantic(text)
            term_counts: dict[str, int] = {}
            for token in tokens:
                term_counts[token] = term_counts.get(token, 0) + 1
            chunk_terms.append((chunk, term_counts))
            for token in term_counts.keys():
                doc_freq[token] = doc_freq.get(token, 0) + 1

        doc_count = max(1, len(chunks))
        idf = {
            token: round(1.0 + math.log((1.0 + doc_count) / (1.0 + freq)), 6)
            for token, freq in doc_freq.items()
        }

        rows: list[dict] = []
        for chunk, term_counts in chunk_terms:
            weights: dict[str, float] = {}
            for token, count in term_counts.items():
                weights[token] = count * idf.get(token, 1.0)
            norm = math.sqrt(sum(weight * weight for weight in weights.values())) or 1.0
            normalized = {token: round(weight / norm, 6) for token, weight in weights.items()}
            top_terms = sorted(normalized.items(), key=lambda item: item[1], reverse=True)[:48]
            rows.append(
                {
                    "chunk_id": chunk.get("chunk_id"),
                    "path": chunk.get("path"),
                    "weights": {token: weight for token, weight in top_terms},
                    "top_terms": [token for token, _ in top_terms[:12]],
                }
            )

        top_vocab = sorted(doc_freq.items(), key=lambda item: item[1], reverse=True)[:20]
        return {
            "doc_count": len(chunks),
            "vocab_size": len(idf),
            "idf": idf,
            "rows": rows,
            "top_terms": [{"name": token, "count": freq} for token, freq in top_vocab],
        }

    def load_semantic_index(self) -> dict:
        if not SEMANTIC_FILE.exists():
            return {"doc_count": 0, "vocab_size": 0, "idf": {}, "rows": [], "top_terms": []}
        try:
            data = json.loads(SEMANTIC_FILE.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                return data
        except json.JSONDecodeError:
            pass
        return {"doc_count": 0, "vocab_size": 0, "idf": {}, "rows": [], "top_terms": []}

    def _semantic_search(self, query: str, limit: int = 8) -> list[dict]:
        semantic = self.load_semantic_index()
        idf = semantic.get("idf", {})
        rows = semantic.get("rows", [])
        if not isinstance(idf, dict) or not isinstance(rows, list):
            return []

        term_counts: dict[str, int] = {}
        for token in self._tokenize_semantic(query):
            if token in idf:
                term_counts[token] = term_counts.get(token, 0) + 1
        if not term_counts:
            return []

        query_weights = {token: count * float(idf.get(token, 1.0)) for token, count in term_counts.items()}
        norm = math.sqrt(sum(weight * weight for weight in query_weights.values())) or 1.0
        query_vector = {token: weight / norm for token, weight in query_weights.items()}

        scored: list[dict] = []
        for row in rows:
            weights = row.get("weights", {})
            if not isinstance(weights, dict):
                continue
            score = 0.0
            for token, query_weight in query_vector.items():
                score += query_weight * float(weights.get(token, 0.0))
            if score > 0:
                scored.append(
                    {
                        "chunk_id": row.get("chunk_id"),
                        "path": row.get("path"),
                        "semantic_score": round(score, 6),
                        "top_terms": row.get("top_terms", []),
                    }
                )
        scored.sort(key=lambda item: item["semantic_score"], reverse=True)
        return scored[:limit]

    def _chunk_text(self, path: str, text: str, max_chars: int = 1800) -> list[Chunk]:
        lines = text.splitlines()
        chunks: list[Chunk] = []
        current: list[str] = []
        current_len = 0
        chunk_index = 0

        for line in lines:
            current.append(line)
            current_len += len(line) + 1
            if current_len >= max_chars:
                chunk_text = "\n".join(current).strip()
                if chunk_text:
                    chunks.append(
                        Chunk(
                            path=path,
                            chunk_id=f"{path}#chunk-{chunk_index}",
                            text=chunk_text,
                            symbols=self._extract_symbols(chunk_text),
                            imports=self._extract_imports(chunk_text),
                            inherits=self._extract_inheritance(chunk_text),
                            calls=self._extract_calls(chunk_text),
                        )
                    )
                    chunk_index += 1
                current = []
                current_len = 0

        if current:
            chunk_text = "\n".join(current).strip()
            if chunk_text:
                chunks.append(
                    Chunk(
                        path=path,
                        chunk_id=f"{path}#chunk-{chunk_index}",
                        text=chunk_text,
                        symbols=self._extract_symbols(chunk_text),
                        imports=self._extract_imports(chunk_text),
                        inherits=self._extract_inheritance(chunk_text),
                        calls=self._extract_calls(chunk_text),
                    )
                )
        return chunks

    def summarize_index(self, manifest: list[dict] | None = None, chunks: list[dict] | None = None) -> dict:
        manifest_rows = manifest if manifest is not None else load_json_list(MANIFEST_FILE)
        chunk_rows = chunks if chunks is not None else load_json_list(CHUNKS_FILE)

        extensions: dict[str, int] = {}
        symbol_counts: dict[str, int] = {}
        import_counts: dict[str, int] = {}
        inherit_counts: dict[str, int] = {}
        function_counts: dict[str, int] = {}
        top_files = sorted(manifest_rows, key=lambda row: int(row.get("size", 0)), reverse=True)[:5]

        for row in manifest_rows:
            suffix = Path(str(row.get("path", ""))).suffix.lower() or "(none)"
            extensions[suffix] = extensions.get(suffix, 0) + 1
            for symbol in row.get("symbols", []):
                symbol_counts[symbol] = symbol_counts.get(symbol, 0) + 1
            for value in row.get("imports", []):
                import_counts[value] = import_counts.get(value, 0) + 1
            for value in row.get("inherits", []):
                inherit_counts[value] = inherit_counts.get(value, 0) + 1
            for value in row.get("function_signatures", []):
                name = value.split("(", 1)[0]
                function_counts[name] = function_counts.get(name, 0) + 1

        sorted_ext = sorted(extensions.items(), key=lambda item: item[1], reverse=True)[:5]
        sorted_symbols = sorted(symbol_counts.items(), key=lambda item: item[1], reverse=True)[:8]
        sorted_imports = sorted(import_counts.items(), key=lambda item: item[1], reverse=True)[:8]
        sorted_inherits = sorted(inherit_counts.items(), key=lambda item: item[1], reverse=True)[:8]
        sorted_functions = sorted(function_counts.items(), key=lambda item: item[1], reverse=True)[:8]
        graph = self.load_graph()
        graph_summary = graph.get("summary", {}) if isinstance(graph, dict) else {}
        semantic = self.load_semantic_index()

        return {
            "top_extensions": [{"name": name, "count": count} for name, count in sorted_ext],
            "top_symbols": [{"name": name, "count": count} for name, count in sorted_symbols],
            "top_imports": [{"name": name, "count": count} for name, count in sorted_imports],
            "top_inheritance": [{"name": name, "count": count} for name, count in sorted_inherits],
            "top_functions": [{"name": name, "count": count} for name, count in sorted_functions],
            "graph_symbol_count": int(graph_summary.get("symbol_count", 0)),
            "graph_file_count": int(graph_summary.get("file_count", 0)),
            "graph_edge_count": int(graph_summary.get("edge_count", 0)),
            "top_connected_symbols": graph_summary.get("top_connected_symbols", [])[:8],
            "semantic_doc_count": int(semantic.get("doc_count", 0)),
            "semantic_vocab_size": int(semantic.get("vocab_size", 0)),
            "semantic_top_terms": semantic.get("top_terms", [])[:8],
            "largest_files": [
                {
                    "path": row.get("path"),
                    "size": row.get("size"),
                    "chunk_count": row.get("chunk_count"),
                }
                for row in top_files
            ],
            "avg_chunk_size": int(sum(len(str(row.get("text", ""))) for row in chunk_rows) / len(chunk_rows))
            if chunk_rows
            else 0,
        }

    def _build_code_graph(self, manifest: list[dict]) -> dict:
        files: dict[str, dict] = {}
        symbols: dict[str, dict] = {}

        def ensure_file_node(path: str) -> dict:
            node = files.get(path)
            if node is None:
                node = {
                    "path": path,
                    "defines": [],
                    "imports": [],
                    "imported_by": [],
                    "inherits": [],
                    "inherited_by": [],
                    "calls": [],
                    "called_by": [],
                    "references": [],
                }
                files[path] = node
            return node

        def ensure_symbol_node(name: str) -> dict:
            node = symbols.get(name)
            if node is None:
                node = {
                    "name": name,
                    "defined_in": [],
                    "referenced_in": [],
                    "called_from": [],
                    "inherits_from": [],
                    "inherited_by": [],
                    "imports_in": [],
                }
                symbols[name] = node
            return node

        def add_unique(items: list[str], value: str) -> None:
            if value and value not in items:
                items.append(value)

        symbol_to_paths: dict[str, list[str]] = {}
        import_to_paths: dict[str, list[str]] = {}

        for row in manifest:
            path = str(row.get("path", ""))
            file_node = ensure_file_node(path)
            imports = [str(value) for value in row.get("imports", []) if str(value).strip()]
            inherits = [str(value) for value in row.get("inherits", []) if str(value).strip()]
            calls = [str(value) for value in row.get("top_calls", []) if str(value).strip()]
            file_node["imports"] = imports[:]
            file_node["inherits"] = inherits[:]
            file_node["calls"] = calls[:]

            defined_symbols = self._unique_list(
                list(row.get("symbol_kinds", {}).get("classes", []))
                + list(row.get("symbol_kinds", {}).get("structs", []))
                + list(row.get("symbol_kinds", {}).get("enums", []))
                + [signature.split("(", 1)[0] for signature in row.get("function_signatures", [])],
                limit=120,
            )
            file_node["defines"] = defined_symbols[:]

            for symbol in defined_symbols:
                ensure_symbol_node(symbol)
                add_unique(symbol_to_paths.setdefault(symbol, []), path)
                add_unique(symbols[symbol]["defined_in"], path)
                add_unique(symbols[symbol]["referenced_in"], path)

            for value in imports:
                ensure_symbol_node(value)
                add_unique(symbols[value]["imports_in"], path)
                add_unique(symbols[value]["referenced_in"], path)
                add_unique(import_to_paths.setdefault(Path(value).name, []), path)
                add_unique(import_to_paths.setdefault(value, []), path)

            for value in inherits:
                ensure_symbol_node(value)
                add_unique(symbols[value]["inherited_by"], path)
                add_unique(symbols[value]["referenced_in"], path)

            for value in calls:
                ensure_symbol_node(value)
                add_unique(symbols[value]["called_from"], path)
                add_unique(symbols[value]["referenced_in"], path)

            for value in self._unique_list(defined_symbols + imports + inherits + calls, limit=200):
                add_unique(file_node["references"], value)

        for path, file_node in files.items():
            for imported in file_node["imports"]:
                candidates = import_to_paths.get(imported, []) + import_to_paths.get(Path(imported).name, [])
                for candidate_path in candidates:
                    if candidate_path != path:
                        add_unique(file_node["imported_by"], candidate_path)
                        add_unique(files[candidate_path]["imports"], imported)

            for inherited_symbol in file_node["inherits"]:
                inherited_node = symbols.get(inherited_symbol)
                if not inherited_node:
                    continue
                for owner_path in inherited_node.get("defined_in", []):
                    if owner_path != path:
                        add_unique(file_node["inherited_by"], owner_path)
                        add_unique(files[owner_path]["inherits"], inherited_symbol)
                inherited_symbol_node = ensure_symbol_node(inherited_symbol)
                add_unique(inherited_symbol_node["inherited_by"], path)

            for called_symbol in file_node["calls"]:
                called_node = symbols.get(called_symbol)
                if not called_node:
                    continue
                for owner_path in called_node.get("defined_in", []):
                    if owner_path != path:
                        add_unique(file_node["called_by"], owner_path)
                        add_unique(files[owner_path]["calls"], called_symbol)

        top_connected = []
        for symbol_name, node in symbols.items():
            connectivity = (
                len(node.get("defined_in", []))
                + len(node.get("referenced_in", []))
                + len(node.get("called_from", []))
                + len(node.get("inherited_by", []))
            )
            if connectivity > 0:
                top_connected.append({"name": symbol_name, "count": connectivity})
        top_connected.sort(key=lambda item: item["count"], reverse=True)

        edge_count = 0
        for node in files.values():
            edge_count += len(node.get("imports", []))
            edge_count += len(node.get("inherits", []))
            edge_count += len(node.get("calls", []))

        return {
            "files": files,
            "symbols": symbols,
            "summary": {
                "file_count": len(files),
                "symbol_count": len(symbols),
                "edge_count": edge_count,
                "top_connected_symbols": top_connected[:12],
            },
        }

    def load_graph(self) -> dict:
        if not GRAPH_FILE.exists():
            return {"files": {}, "symbols": {}, "summary": {"file_count": 0, "symbol_count": 0, "edge_count": 0, "top_connected_symbols": []}}
        try:
            data = json.loads(GRAPH_FILE.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                return data
        except json.JSONDecodeError:
            pass
        return {"files": {}, "symbols": {}, "summary": {"file_count": 0, "symbol_count": 0, "edge_count": 0, "top_connected_symbols": []}}

    def index_project(self) -> dict:
        with self._lock:
            files = self._iter_project_files()
            manifest: list[dict] = []
            all_chunks: list[dict] = []
            truncated_file_count = 0
            fast_mode = len(files) > FAST_INDEX_FILE_THRESHOLD
            max_text_chars = FAST_INDEX_TEXT_CHARS_PER_FILE if fast_mode else MAX_TEXT_CHARS_PER_FILE
            max_chunks_per_file = FAST_INDEX_CHUNKS_PER_FILE if fast_mode else MAX_CHUNKS_PER_FILE
            chunk_chars = FAST_INDEX_CHUNK_CHARS if fast_mode else 1800

            for path in files:
                stat = path.stat()
                text = read_text_file(path, max_chars=max_text_chars)
                relative = self._project_relative(path)
                was_truncated = len(text) >= max_text_chars
                if was_truncated:
                    truncated_file_count += 1
                symbols = self._extract_symbols(text)
                imports = self._extract_imports(text)
                inherits = self._extract_inheritance(text)
                function_signatures = self._extract_function_signatures(text)
                symbol_kinds = self._extract_symbol_kinds(text)
                calls = self._extract_calls(text)
                chunks = self._chunk_text(relative, text, max_chars=chunk_chars)[:max_chunks_per_file]
                manifest.append(
                    {
                        "path": relative,
                        "size": stat.st_size,
                        "mtime": stat.st_mtime,
                        "symbols": symbols[:MAX_SYMBOLS_PER_FILE],
                        "imports": imports[:MAX_IMPORTS_PER_FILE],
                        "inherits": inherits[:MAX_INHERITS_PER_FILE],
                        "function_signatures": function_signatures[:MAX_FUNCTIONS_PER_FILE],
                        "symbol_kinds": symbol_kinds,
                        "top_calls": calls[:MAX_CALLS_PER_FILE],
                        "chunk_count": len(chunks),
                        "truncated_for_index": was_truncated,
                    }
                )
                all_chunks.extend(
                    {
                        "path": chunk.path,
                        "chunk_id": chunk.chunk_id,
                        "text": chunk.text,
                        "symbols": chunk.symbols,
                        "imports": chunk.imports,
                        "inherits": chunk.inherits,
                        "calls": chunk.calls,
                    }
                    for chunk in chunks
                )

            if fast_mode:
                graph = {
                    "files": {},
                    "symbols": {},
                    "summary": {
                        "file_count": len(manifest),
                        "symbol_count": 0,
                        "edge_count": 0,
                        "top_connected_symbols": [],
                    },
                }
                semantic = {
                    "doc_count": len(all_chunks),
                    "vocab_size": 0,
                    "idf": {},
                    "rows": [],
                    "top_terms": [],
                }
            else:
                graph = self._build_code_graph(manifest)
                semantic = self._build_semantic_index(all_chunks)
            save_json_list(MANIFEST_FILE, manifest)
            save_json_list(CHUNKS_FILE, all_chunks)
            GRAPH_FILE.write_text(json.dumps(graph, indent=2, ensure_ascii=True), encoding="utf-8")
            SEMANTIC_FILE.write_text(json.dumps(semantic, indent=2, ensure_ascii=True), encoding="utf-8")
            status = {
                "indexed_at": datetime.now(UTC).isoformat(),
                "file_count": len(manifest),
                "chunk_count": len(all_chunks),
                "truncated_file_count": truncated_file_count,
                "index_mode": "fast" if fast_mode else "deep",
                "roots": [str(root) for root in self.project_roots],
                "summary": self.summarize_index(manifest, all_chunks),
                "graph": graph.get("summary", {}),
                "semantic": {
                    "doc_count": semantic.get("doc_count", 0),
                    "vocab_size": semantic.get("vocab_size", 0),
                    "top_terms": semantic.get("top_terms", [])[:8],
                },
                "index_limits": {
                    "max_text_chars_per_file": max_text_chars,
                    "max_chunks_per_file": max_chunks_per_file,
                    "chunk_chars": chunk_chars,
                },
            }
            STATUS_FILE.write_text(json.dumps(status, indent=2, ensure_ascii=True), encoding="utf-8")
            return status

    def get_status(self) -> dict:
        if not STATUS_FILE.exists():
            return {
                "indexed_at": None,
                "file_count": 0,
                "chunk_count": 0,
                "truncated_file_count": 0,
                "index_mode": "deep",
                "roots": [str(root) for root in self.project_roots],
                "summary": self.summarize_index([], []),
                "graph": self.load_graph().get("summary", {}),
                "semantic": {
                    "doc_count": 0,
                    "vocab_size": 0,
                    "top_terms": [],
                },
                "index_limits": {
                    "max_text_chars_per_file": MAX_TEXT_CHARS_PER_FILE,
                    "max_chunks_per_file": MAX_CHUNKS_PER_FILE,
                    "chunk_chars": 1800,
                },
            }
        try:
            status = json.loads(STATUS_FILE.read_text(encoding="utf-8"))
            if "summary" not in status:
                status["summary"] = self.summarize_index()
            if "graph" not in status:
                status["graph"] = self.load_graph().get("summary", {})
            if "semantic" not in status:
                semantic = self.load_semantic_index()
                status["semantic"] = {
                    "doc_count": semantic.get("doc_count", 0),
                    "vocab_size": semantic.get("vocab_size", 0),
                    "top_terms": semantic.get("top_terms", [])[:8],
                }
            if "truncated_file_count" not in status:
                status["truncated_file_count"] = 0
            if "index_mode" not in status:
                status["index_mode"] = "deep"
            if "index_limits" not in status:
                status["index_limits"] = {
                    "max_text_chars_per_file": MAX_TEXT_CHARS_PER_FILE,
                    "max_chunks_per_file": MAX_CHUNKS_PER_FILE,
                    "chunk_chars": 1800,
                }
            return status
        except json.JSONDecodeError:
            return {
                "indexed_at": None,
                "file_count": 0,
                "chunk_count": 0,
                "truncated_file_count": 0,
                "index_mode": "deep",
                "roots": [str(root) for root in self.project_roots],
                "summary": self.summarize_index([], []),
                "graph": self.load_graph().get("summary", {}),
                "semantic": {
                    "doc_count": 0,
                    "vocab_size": 0,
                    "top_terms": [],
                },
                "index_limits": {
                    "max_text_chars_per_file": MAX_TEXT_CHARS_PER_FILE,
                    "max_chunks_per_file": MAX_CHUNKS_PER_FILE,
                    "chunk_chars": 1800,
                },
            }

    def ensure_index(self) -> dict:
        status = self.get_status()
        if status.get("file_count", 0) == 0:
            return self.index_project()
        return status

    def search(self, query: str, limit: int = 8) -> list[dict]:
        query_terms = [term.lower() for term in query.split() if term.strip()]
        chunks = load_json_list(CHUNKS_FILE)
        scored: list[tuple[int, dict]] = []

        for chunk in chunks:
            haystack = " ".join(
                [
                    str(chunk.get("path", "")),
                    str(chunk.get("text", "")),
                    " ".join(chunk.get("symbols", [])),
                    " ".join(chunk.get("imports", [])),
                    " ".join(chunk.get("inherits", [])),
                    " ".join(chunk.get("calls", [])),
                ]
            ).lower()
            score = sum(haystack.count(term) for term in query_terms)
            if score > 0:
                scored.append((score, chunk))

        scored.sort(key=lambda item: item[0], reverse=True)
        lexical_results = {
            chunk["chunk_id"]: {
                "path": chunk["path"],
                "chunk_id": chunk["chunk_id"],
                "symbols": chunk.get("symbols", []),
                "imports": chunk.get("imports", []),
                "inherits": chunk.get("inherits", []),
                "calls": chunk.get("calls", []),
                "snippet": str(chunk.get("text", ""))[:1000],
                "score": score,
                "lexical_score": score,
                "semantic_score": 0.0,
                "match_type": "lexical",
            }
            for score, chunk in scored[:limit * 3]
        }

        semantic_results = self._semantic_search(query, limit=limit * 3)
        chunk_lookup = {str(chunk.get("chunk_id")): chunk for chunk in chunks}

        for row in semantic_results:
            chunk_id = str(row.get("chunk_id", ""))
            base = lexical_results.get(chunk_id)
            semantic_score = float(row.get("semantic_score", 0.0))
            if base is None:
                chunk = chunk_lookup.get(chunk_id, {})
                lexical_results[chunk_id] = {
                    "path": row.get("path") or chunk.get("path"),
                    "chunk_id": chunk_id,
                    "symbols": chunk.get("symbols", []),
                    "imports": chunk.get("imports", []),
                    "inherits": chunk.get("inherits", []),
                    "calls": chunk.get("calls", []),
                    "snippet": str(chunk.get("text", ""))[:1000],
                    "score": semantic_score * 10.0,
                    "lexical_score": 0,
                    "semantic_score": semantic_score,
                    "semantic_terms": row.get("top_terms", []),
                    "match_type": "semantic",
                }
            else:
                base["semantic_score"] = semantic_score
                base["semantic_terms"] = row.get("top_terms", [])
                base["score"] = float(base.get("lexical_score", 0)) + semantic_score * 10.0
                base["match_type"] = "hybrid" if base.get("lexical_score", 0) else "semantic"

        merged = sorted(lexical_results.values(), key=lambda item: float(item.get("score", 0.0)), reverse=True)
        return merged[:limit]

    def inspect_graph(self, query: str, limit: int = 8) -> dict:
        graph = self.load_graph()
        files = graph.get("files", {})
        symbols = graph.get("symbols", {})
        needle = query.strip().lower()
        if not needle:
            raise ValueError("inspect_graph requires a query")

        symbol_matches: list[dict] = []
        for name, node in symbols.items():
            if needle in name.lower():
                symbol_matches.append(
                    {
                        "name": name,
                        "defined_in": node.get("defined_in", [])[:6],
                        "referenced_in": node.get("referenced_in", [])[:6],
                        "called_from": node.get("called_from", [])[:6],
                        "inherits_from": node.get("inherits_from", [])[:6],
                        "inherited_by": node.get("inherited_by", [])[:6],
                        "imports_in": node.get("imports_in", [])[:6],
                        "score": len(node.get("referenced_in", [])) + len(node.get("called_from", [])) + len(node.get("defined_in", [])),
                    }
                )
        symbol_matches.sort(key=lambda item: item["score"], reverse=True)

        file_matches: list[dict] = []
        for path, node in files.items():
            if needle in path.lower():
                file_matches.append(
                    {
                        "path": path,
                        "defines": node.get("defines", [])[:10],
                        "imports": node.get("imports", [])[:10],
                        "inherits": node.get("inherits", [])[:10],
                        "calls": node.get("calls", [])[:10],
                        "references": node.get("references", [])[:12],
                        "score": len(node.get("references", [])) + len(node.get("calls", [])),
                    }
                )
        file_matches.sort(key=lambda item: item["score"], reverse=True)

        return {
            "query": query,
            "symbol_matches": symbol_matches[:limit],
            "file_matches": file_matches[:limit],
            "summary": graph.get("summary", {}),
        }

    def read_project_file(self, relative_path: str, max_chars: int = 12000) -> dict:
        normalized = relative_path.strip().replace("\\", "/")
        candidate = self._resolve_project_path(normalized)
        return {
            "path": normalized,
            "content": read_text_file(candidate)[:max_chars],
        }
