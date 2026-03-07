"""Utilities for agent 192.168.88.10."""

from __future__ import annotations

from ingestion.query_interface import KnowledgeQueryService


knowledge_service = KnowledgeQueryService()


def retrieve_context(question: str, top_k: int = 5) -> list[dict[str, object]]:
    """Shared retrieval entrypoint for debate context gathering."""
    return [result.__dict__ for result in knowledge_service.query(question, top_k=top_k)]
