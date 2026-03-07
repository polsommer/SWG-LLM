"""Chunking and indexing logic for repository documents."""

from __future__ import annotations

from dataclasses import dataclass
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

            step = self.chunk_lines - self.overlap_lines
            for start in range(0, len(lines), step):
                end = min(len(lines), start + self.chunk_lines)
                chunk_lines = lines[start:end]
                chunk_text = "\n".join(chunk_lines).strip()
                if not chunk_text:
                    continue

                digest = sha1(chunk_text.encode("utf-8")).hexdigest()
                chunk_id = f"{rel_path}:{start + 1}:{end}:{digest[:12]}"
                yield Chunk(
                    chunk_id=chunk_id,
                    file_path=rel_path,
                    start_line=start + 1,
                    end_line=end,
                    text=chunk_text,
                    content_hash=digest,
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
