"""Configuration utilities for SWG-LLM."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, List, Sequence


@dataclass(slots=True)
class SWGConfig:
    """Configuration for the SWG LLM assistant.

    Attributes
    ----------
    repository_root:
        Root directory containing the codebase that should be indexed.
    index_dir:
        Directory where the vector store and metadata will be saved.
    embedding_model:
        Hugging Face model name used for computing sentence embeddings.
    generator_model:
        Hugging Face model name used for code/text generation.
    cpu_only:
        When ``True``, force the generator to run entirely on CPU-friendly
        settings suitable for low-resource devices.
    include_globs:
        Optional iterable of glob patterns to include. If empty, all files
        under ``repository_root`` are considered.
    exclude_globs:
        Glob patterns to ignore when scanning the repository.
    chunk_size:
        Maximum number of characters per chunk before computing embeddings.
    chunk_overlap:
        Number of overlapping characters between consecutive chunks.
    top_k:
        Number of retrieved chunks to include as context during generation.
    """

    repository_root: Path
    index_dir: Path
    embedding_model: str = "sentence-transformers/all-MiniLM-L6-v2"
    generator_model: str = "microsoft/phi-2"
    cpu_only: bool = False
    include_globs: Sequence[str] = field(default_factory=tuple)
    exclude_globs: Sequence[str] = field(
        default_factory=lambda: ("**/.git/**", "**/__pycache__/**", "**/*.pyc")
    )
    chunk_size: int = 1200
    chunk_overlap: int = 120
    top_k: int = 4

    def expanded_include_globs(self) -> List[str]:
        """Return a list of include globs or a default of all files."""

        includes: List[str] = list(self.include_globs)
        if not includes:
            includes.append("**/*")
        return includes

    def iter_repository_files(self) -> Iterable[Path]:
        """Yield repository files that match include globs and avoid exclusions."""

        root = self.repository_root
        excludes = tuple(self.exclude_globs)
        for pattern in self.expanded_include_globs():
            for file_path in root.glob(pattern):
                if file_path.is_dir():
                    continue
                if any(file_path.match(ex) for ex in excludes):
                    continue
                yield file_path

    def ensure_index_dir(self) -> Path:
        """Ensure the index directory exists and return it."""

        self.index_dir.mkdir(parents=True, exist_ok=True)
        return self.index_dir
