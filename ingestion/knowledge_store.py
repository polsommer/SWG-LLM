"""Persistent hybrid index with incremental revision tracking and retrieval telemetry."""

from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import json
import math
import os
from pathlib import Path
import re
from typing import Any

from .chunk_indexer import ChunkIndexer


_TOKEN_RE = re.compile(r"[a-zA-Z0-9_]{2,}")


@dataclass(frozen=True)
class QueryResult:
    chunk_id: str
    score: float
    file_path: str
    start_line: int
    end_line: int
    text: str
    semantic_score: float
    keyword_score: float
    rerank_score: float
    document_title: str
    section: str
    last_updated: str
    access_scope: str
    source_kind: str


class KnowledgeStore:
    """Vector store backed by JSON artifacts for reproducible indexing/query."""

    def __init__(self, store_dir: Path | None = None, dimensions: int = 256) -> None:
        base = store_dir or Path(os.getenv("SWG_WORKDIR", ".swg")) / "knowledge"
        self.store_dir = base.resolve()
        self.store_dir.mkdir(parents=True, exist_ok=True)
        self.index_file = self.store_dir / "index.json"
        self.metrics_file = self.store_dir / "retrieval_metrics.jsonl"
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
                "indexed_sources": self._source_distribution(prior_chunks),
            }

        seen_ids: set[str] = set()
        next_chunks: dict[str, Any] = {}
        embedded_new = 0
        document_frequency: Counter[str] = Counter()

        for chunk in indexer.iter_chunks():
            seen_ids.add(chunk.chunk_id)
            previous = prior_chunks.get(chunk.chunk_id)
            if previous and previous.get("content_hash") == chunk.content_hash:
                next_chunks[chunk.chunk_id] = previous
            else:
                vector = self._embed_text(chunk.text)
                payload = {
                    **asdict(chunk),
                    "vector": vector,
                }
                next_chunks[chunk.chunk_id] = payload
                embedded_new += 1

            tokens = set(self._tokenize(next_chunks[chunk.chunk_id]["text"]))
            for token in tokens:
                document_frequency[token] += 1

        deleted = [chunk_id for chunk_id in prior_chunks if chunk_id not in seen_ids]
        self._state = {
            "repository_root": str(repo_root),
            "revision": revision,
            "dimensions": self.dimensions,
            "chunks": next_chunks,
            "deleted_chunk_ids": deleted,
            "document_frequency": dict(document_frequency),
            "total_documents": len(next_chunks),
        }
        self._persist()

        return {
            "status": "updated",
            "revision": revision,
            "indexed_chunks": len(next_chunks),
            "embedded_new_chunks": embedded_new,
            "deleted_chunks": len(deleted),
            "indexed_sources": self._source_distribution(next_chunks),
        }

    def query(self, query_text: str, top_k: int = 5) -> list[QueryResult]:
        if not self._state.get("chunks"):
            self._log_retrieval(query_text, top_k, [], hit=False)
            return []

        query_vector = self._embed_text(query_text)
        query_tokens = self._tokenize(query_text)
        ranked = self._hybrid_rank(query_vector, query_tokens)

        results = [self._to_query_result(item) for item in ranked[: max(1, top_k)]]
        self._log_retrieval(query_text, top_k, results, hit=bool(results))
        return results

    def _hybrid_rank(self, query_vector: list[float], query_tokens: list[str]) -> list[dict[str, Any]]:
        chunks = self._state.get("chunks", {})
        if not chunks:
            return []

        bm25_scores = self._bm25_scores(query_tokens)
        semantic_scores: dict[str, float] = {}
        keyword_overlap: dict[str, float] = {}

        for chunk_id, payload in chunks.items():
            semantic = self._cosine_similarity(query_vector, payload["vector"])
            semantic_scores[chunk_id] = semantic

            chunk_tokens = set(self._tokenize(payload["text"]))
            overlap = len(set(query_tokens) & chunk_tokens)
            keyword_overlap[chunk_id] = overlap / max(1, len(set(query_tokens)))

        max_semantic = max(semantic_scores.values(), default=1.0) or 1.0
        max_bm25 = max(bm25_scores.values(), default=1.0) or 1.0

        combined: list[dict[str, Any]] = []
        for chunk_id, payload in chunks.items():
            semantic_norm = semantic_scores[chunk_id] / max_semantic
            bm25_norm = bm25_scores.get(chunk_id, 0.0) / max_bm25
            rerank = 0.55 * semantic_norm + 0.35 * bm25_norm + 0.10 * keyword_overlap[chunk_id]

            combined.append(
                {
                    "chunk_id": chunk_id,
                    "payload": payload,
                    "semantic_score": semantic_scores[chunk_id],
                    "keyword_score": bm25_scores.get(chunk_id, 0.0),
                    "rerank_score": rerank,
                    "score": rerank,
                }
            )

        combined.sort(key=lambda item: item["rerank_score"], reverse=True)
        return combined

    def _bm25_scores(self, query_tokens: list[str]) -> dict[str, float]:
        chunks = self._state.get("chunks", {})
        total_docs = max(1, int(self._state.get("total_documents", len(chunks) or 1)))
        doc_freq = self._state.get("document_frequency", {})

        doc_lengths: dict[str, int] = {}
        term_freqs: dict[str, Counter[str]] = {}
        total_length = 0

        for chunk_id, payload in chunks.items():
            tokens = self._tokenize(payload["text"])
            total_length += len(tokens)
            doc_lengths[chunk_id] = len(tokens)
            term_freqs[chunk_id] = Counter(tokens)

        avg_doc_length = total_length / max(1, len(chunks))
        k1 = 1.5
        b = 0.75
        scores: dict[str, float] = defaultdict(float)

        for token in query_tokens:
            if token not in doc_freq:
                continue
            idf = math.log((total_docs - doc_freq[token] + 0.5) / (doc_freq[token] + 0.5) + 1)
            for chunk_id, tf in term_freqs.items():
                freq = tf.get(token, 0)
                if freq == 0:
                    continue
                denom = freq + k1 * (1 - b + b * doc_lengths[chunk_id] / max(1e-6, avg_doc_length))
                scores[chunk_id] += idf * ((freq * (k1 + 1)) / denom)
        return scores

    def _to_query_result(self, item: dict[str, Any]) -> QueryResult:
        payload = item["payload"]
        return QueryResult(
            chunk_id=item["chunk_id"],
            score=item["score"],
            file_path=payload["file_path"],
            start_line=payload["start_line"],
            end_line=payload["end_line"],
            text=payload["text"],
            semantic_score=item["semantic_score"],
            keyword_score=item["keyword_score"],
            rerank_score=item["rerank_score"],
            document_title=payload.get("document_title", "unknown"),
            section=payload.get("section", "root"),
            last_updated=payload.get("last_updated", "unknown"),
            access_scope=payload.get("access_scope", "restricted"),
            source_kind=payload.get("source_kind", "code"),
        )

    def _load(self) -> dict[str, Any]:
        if not self.index_file.exists():
            return {
                "revision": None,
                "chunks": {},
                "dimensions": self.dimensions,
                "document_frequency": {},
                "total_documents": 0,
            }
        return json.loads(self.index_file.read_text(encoding="utf-8"))

    def _persist(self) -> None:
        self.index_file.write_text(json.dumps(self._state, indent=2), encoding="utf-8")

    def _embed_text(self, text: str) -> list[float]:
        """Hashing-based embedding to avoid external model dependencies."""
        vector = [0.0] * self.dimensions
        for token in self._tokenize(text):
            idx = hash(token) % self.dimensions
            vector[idx] += 1.0

        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0:
            return vector
        return [value / norm for value in vector]

    @staticmethod
    def _cosine_similarity(lhs: list[float], rhs: list[float]) -> float:
        return sum(a * b for a, b in zip(lhs, rhs))

    @staticmethod
    def _tokenize(text: str) -> list[str]:
        return [tok.lower() for tok in _TOKEN_RE.findall(text)]

    @staticmethod
    def _source_distribution(chunks: dict[str, Any]) -> dict[str, int]:
        counts: dict[str, int] = defaultdict(int)
        for payload in chunks.values():
            counts[payload.get("source_kind", "unknown")] += 1
        return dict(sorted(counts.items()))

    def _log_retrieval(self, query: str, top_k: int, results: list[QueryResult], hit: bool) -> None:
        event = {
            "timestamp": datetime.now(tz=timezone.utc).isoformat(),
            "query": query,
            "top_k": top_k,
            "hit": hit,
            "results": [
                {
                    "chunk_id": result.chunk_id,
                    "file_path": result.file_path,
                    "score": round(result.score, 5),
                    "semantic_score": round(result.semantic_score, 5),
                    "keyword_score": round(result.keyword_score, 5),
                    "rerank_score": round(result.rerank_score, 5),
                }
                for result in results
            ],
        }
        with self.metrics_file.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(event) + "\n")
