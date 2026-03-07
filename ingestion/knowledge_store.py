"""Persistent semantic index with incremental revision tracking."""

from __future__ import annotations

from dataclasses import asdict, dataclass
import json
import math
import os
from pathlib import Path
from typing import Any

from .chunk_indexer import Chunk, ChunkIndexer


@dataclass(frozen=True)
class QueryResult:
    chunk_id: str
    score: float
    file_path: str
    start_line: int
    end_line: int
    text: str


class KnowledgeStore:
    """Vector store backed by JSON artifacts for reproducible indexing/query."""

    def __init__(self, store_dir: Path | None = None, dimensions: int = 256) -> None:
        base = store_dir or Path(os.getenv("SWG_WORKDIR", ".swg")) / "knowledge"
        self.store_dir = base.resolve()
        self.store_dir.mkdir(parents=True, exist_ok=True)
        self.index_file = self.store_dir / "index.json"
        self.dimensions = dimensions
        self._state = self._load()

    def index_repository(self, repo_root: Path, revision: str, indexer: ChunkIndexer) -> dict[str, Any]:
        prior_revision = self._state.get("revision")
        prior_chunks = self._state.get("chunks", {})

        if revision == prior_revision:
            return {
                "status": "unchanged",
                "revision": revision,
                "indexed_chunks": len(prior_chunks),
                "embedded_new_chunks": 0,
            }

        seen_ids: set[str] = set()
        next_chunks: dict[str, Any] = {}
        embedded_new = 0

        for chunk in indexer.iter_chunks():
            seen_ids.add(chunk.chunk_id)
            previous = prior_chunks.get(chunk.chunk_id)
            if previous and previous.get("content_hash") == chunk.content_hash:
                next_chunks[chunk.chunk_id] = previous
                continue

            vector = self._embed_text(chunk.text)
            next_chunks[chunk.chunk_id] = {
                **asdict(chunk),
                "vector": vector,
            }
            embedded_new += 1

        deleted = [chunk_id for chunk_id in prior_chunks if chunk_id not in seen_ids]
        self._state = {
            "repository_root": str(repo_root),
            "revision": revision,
            "dimensions": self.dimensions,
            "chunks": next_chunks,
            "deleted_chunk_ids": deleted,
        }
        self._persist()

        return {
            "status": "updated",
            "revision": revision,
            "indexed_chunks": len(next_chunks),
            "embedded_new_chunks": embedded_new,
            "deleted_chunks": len(deleted),
        }

    def query(self, query_text: str, top_k: int = 5) -> list[QueryResult]:
        query_vector = self._embed_text(query_text)
        scored: list[QueryResult] = []

        for chunk_id, payload in self._state.get("chunks", {}).items():
            score = self._cosine_similarity(query_vector, payload["vector"])
            scored.append(
                QueryResult(
                    chunk_id=chunk_id,
                    score=score,
                    file_path=payload["file_path"],
                    start_line=payload["start_line"],
                    end_line=payload["end_line"],
                    text=payload["text"],
                )
            )

        scored.sort(key=lambda item: item.score, reverse=True)
        return scored[: max(1, top_k)]

    def _load(self) -> dict[str, Any]:
        if not self.index_file.exists():
            return {"revision": None, "chunks": {}, "dimensions": self.dimensions}
        return json.loads(self.index_file.read_text(encoding="utf-8"))

    def _persist(self) -> None:
        self.index_file.write_text(json.dumps(self._state, indent=2), encoding="utf-8")

    def _embed_text(self, text: str) -> list[float]:
        """Hashing-based embedding to avoid external model dependencies."""
        vector = [0.0] * self.dimensions
        for token in text.lower().split():
            if not token:
                continue
            idx = hash(token) % self.dimensions
            vector[idx] += 1.0

        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0:
            return vector
        return [value / norm for value in vector]

    @staticmethod
    def _cosine_similarity(lhs: list[float], rhs: list[float]) -> float:
        return sum(a * b for a, b in zip(lhs, rhs))
