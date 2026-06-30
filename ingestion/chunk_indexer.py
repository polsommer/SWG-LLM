"""Chunking and indexing logic for repository documents."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from hashlib import sha1
from pathlib import Path
from typing import Iterator, Sequence


DEFAULT_EXTENSIONS = (
    ".md",
    ".mdx",
    ".txt",
    ".rst",
    ".py",
    ".yaml",
    ".yml",
    ".json",
    ".toml",
)


@dataclass(frozen=True)
class Chunk:
    """A semantic chunk extracted from a source file."""

    chunk_id: str
    file_path: str
    start_line: int
    end_line: int
    text: str
    content_hash: str
    document_title: str
    section: str
    last_updated: str
    access_scope: str
    source_kind: str


class ChunkIndexer:
    """Walk files and emit chunks based on extension filter and chunking policy."""

    def __init__(
        self,
        root: Path,
        extensions: Sequence[str] = DEFAULT_EXTENSIONS,
        chunk_lines: int = 80,
        overlap_lines: int = 10,
    ) -> None:
        self.root = root
        self.extensions = {ext.lower() for ext in extensions}
        self.chunk_lines = max(1, chunk_lines)
        self.overlap_lines = max(0, min(overlap_lines, self.chunk_lines - 1))

    def iter_chunks(self) -> Iterator[Chunk]:
        for file_path in self._iter_files():
            rel_path = file_path.relative_to(self.root).as_posix()
            text = file_path.read_text(encoding="utf-8", errors="ignore")
            lines = text.splitlines()
            if not lines:
                continue

            title = self._document_title(file_path, lines)
            section_map = self._section_lookup(lines)
            last_updated = self._last_updated(file_path)
            source_kind = self._source_kind(rel_path)
            access_scope = self._access_scope(rel_path)

            step = self.chunk_lines - self.overlap_lines
            for start in range(0, len(lines), step):
                end = min(len(lines), start + self.chunk_lines)
                chunk_lines = lines[start:end]
                chunk_text = "\n".join(chunk_lines).strip()
                if not chunk_text:
                    continue

                digest = sha1(chunk_text.encode("utf-8")).hexdigest()
                chunk_id = f"{rel_path}:{start + 1}:{end}:{digest[:12]}"
                section = self._section_for_range(section_map, start + 1, end)
                yield Chunk(
                    chunk_id=chunk_id,
                    file_path=rel_path,
                    start_line=start + 1,
                    end_line=end,
                    text=chunk_text,
                    content_hash=digest,
                    document_title=title,
                    section=section,
                    last_updated=last_updated,
                    access_scope=access_scope,
                    source_kind=source_kind,
                )

                if end == len(lines):
                    break

    def _iter_files(self) -> Iterator[Path]:
        for path in self.root.rglob("*"):
            if not path.is_file():
                continue
            if ".git" in path.parts:
                continue
            if path.suffix.lower() in self.extensions:
                yield path

    @staticmethod
    def _document_title(file_path: Path, lines: list[str]) -> str:
        for line in lines[:25]:
            stripped = line.strip()
            if stripped.startswith("#"):
                return stripped.lstrip("#").strip() or file_path.stem
        return file_path.stem.replace("_", " ").replace("-", " ").strip() or file_path.name

    @staticmethod
    def _section_lookup(lines: list[str]) -> list[tuple[int, str]]:
        sections: list[tuple[int, str]] = [(1, "root")]
        for idx, line in enumerate(lines, start=1):
            stripped = line.strip()
            if stripped.startswith("#"):
                sections.append((idx, stripped.lstrip("#").strip() or f"section@{idx}"))
                continue
            if stripped and stripped.endswith(":") and len(stripped.split()) <= 8:
                sections.append((idx, stripped.rstrip(":")))
        sections.sort(key=lambda item: item[0])
        return sections

    @staticmethod
    def _section_for_range(sections: list[tuple[int, str]], start_line: int, end_line: int) -> str:
        best = "root"
        for line_number, section in sections:
            if line_number > end_line:
                break
            if line_number >= start_line:
                best = section
        if best != "root":
            return best
        for line_number, section in sections:
            if line_number > start_line:
                break
            best = section
        return best

    @staticmethod
    def _last_updated(file_path: Path) -> str:
        return datetime.fromtimestamp(file_path.stat().st_mtime, tz=timezone.utc).isoformat()

    @staticmethod
    def _source_kind(rel_path: str) -> str:
        lowered = rel_path.lower()
        if lowered.startswith("docs/"):
            return "internal_docs"
        if "wiki" in lowered:
            return "wiki"
        if "log" in lowered:
            return "support_logs"
        return "code"

    @staticmethod
    def _access_scope(rel_path: str) -> str:
        lowered = rel_path.lower()
        if "internal" in lowered or "private" in lowered or "secret" in lowered:
            return "internal"
        if lowered.startswith("docs/") or lowered.startswith("readme"):
            return "public"
        return "restricted"
