"""Shared query interface consumed by debating agents."""

from __future__ import annotations

from pathlib import Path

from .chunk_indexer import ChunkIndexer
from .knowledge_store import KnowledgeStore, QueryResult
from .repository_ingestor import RepositoryIngestor


class KnowledgeQueryService:
    """High-level ingestion+query facade for both debating agents."""

    def __init__(self, workspace: Path | None = None) -> None:
        self.repository_ingestor = RepositoryIngestor(workspace=workspace)
        self.knowledge_store = KnowledgeStore()

    def refresh(self) -> dict[str, object]:
        repo_state = self.repository_ingestor.sync()
        indexer = ChunkIndexer(repo_state.local_path)
        return self.knowledge_store.index_repository(
            repo_root=repo_state.local_path,
            revision=repo_state.revision,
            indexer=indexer,
        )

    def query(self, question: str, top_k: int = 5) -> list[QueryResult]:
        return self.knowledge_store.query(question, top_k=top_k)
