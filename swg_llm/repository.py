"""Repository ingestion helpers."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Iterator, List, Sequence

from .config import SWGConfig


@dataclass(slots=True)
class DocumentChunk:
    """Represents a chunk of text extracted from the repository."""

    source_path: Path
    text: str
    start_line: int
    end_line: int

    def to_metadata(self) -> dict:
        """Serialize chunk metadata."""

        return {
            "source_path": str(self.source_path),
            "start_line": self.start_line,
            "end_line": self.end_line,
        }


class RepositoryLoader:
    """Load and chunk repository files according to the configuration."""

    def __init__(self, config: SWGConfig) -> None:
        self.config = config

    def load(self) -> List[DocumentChunk]:
        """Load repository files and create chunks."""

        chunks: List[DocumentChunk] = []
        for file_path in self.config.iter_repository_files():
            chunks.extend(self._chunk_file(file_path))
        return chunks

    def _chunk_file(self, file_path: Path) -> Iterable[DocumentChunk]:
        text = file_path.read_text(encoding="utf-8", errors="ignore")
        lines = text.splitlines()
        chunk_size = self.config.chunk_size
        overlap = self.config.chunk_overlap
        start = 0
        while start < len(text):
            end = min(len(text), start + chunk_size)
            chunk_text = text[start:end]
            # Map character positions to approximate line numbers.
            start_line = self._position_to_line(lines, start)
            end_line = self._position_to_line(lines, end)
            chunks = DocumentChunk(
                source_path=file_path,
                text=chunk_text,
                start_line=start_line,
                end_line=end_line,
            )
            yield chunks
            start = max(end - overlap, start + 1)

    @staticmethod
    def _position_to_line(lines: Sequence[str], position: int) -> int:
        """Approximate the line number for a character position."""

        total = 0
        for index, line in enumerate(lines, start=1):
            total += len(line) + 1  # account for newline
            if total >= position:
                return index
        return len(lines)

    @staticmethod
    def iter_chunks_by_file(chunks: Sequence[DocumentChunk]) -> Iterator[tuple[Path, List[DocumentChunk]]]:
        """Group chunks by source file path."""

        buckets: dict[Path, List[DocumentChunk]] = {}
        for chunk in chunks:
            buckets.setdefault(chunk.source_path, []).append(chunk)
        for path, group in buckets.items():
            yield path, group
