"""Durable, scoped memory primitives for safe continuity across requests."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import uuid
from typing import Any


MemoryScope = str
MEMORY_SCOPE_SESSION: MemoryScope = "session"
MEMORY_SCOPE_USER: MemoryScope = "user"
MEMORY_SCOPE_TEAM: MemoryScope = "team"
MEMORY_SCOPE_GLOBAL: MemoryScope = "global"


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


@dataclass(frozen=True)
class MemoryRequestContext:
    tenant_id: str
    user_id: str
    team_id: str | None = None
    session_id: str | None = None


@dataclass
class MemoryItem:
    tenant_id: str
    scope: MemoryScope
    content: str
    category: str
    item_id: str = field(default_factory=lambda: str(uuid.uuid4()))
    user_id: str | None = None
    team_id: str | None = None
    session_id: str | None = None
    recurring_goal: str | None = None
    output_format: str | None = None
    tone: str | None = None
    created_at: str = field(default_factory=lambda: _utc_now().isoformat())
    updated_at: str = field(default_factory=lambda: _utc_now().isoformat())
    expires_at: str | None = None

    def is_expired(self, now: datetime | None = None) -> bool:
        if self.expires_at is None:
            return False
        reference = now or _utc_now()
        return datetime.fromisoformat(self.expires_at) <= reference


@dataclass(frozen=True)
class MemoryInjection:
    request_context: MemoryRequestContext
    query: str
    items: tuple[MemoryItem, ...]


@dataclass(frozen=True)
class MemoryTelemetryEvent:
    request_id: str
    tenant_id: str
    memory_item_ids: tuple[str, ...]
    usefulness_score: float
    hallucination_risk_score: float


class MemoryTelemetry:
    """Records usefulness vs hallucination risk for injected memory."""

    def __init__(self) -> None:
        self._events: list[MemoryTelemetryEvent] = []

    def record(self, event: MemoryTelemetryEvent) -> None:
        self._events.append(event)

    def summary(self) -> dict[str, float]:
        if not self._events:
            return {"events": 0.0, "avg_usefulness": 0.0, "avg_hallucination_risk": 0.0}
        usefulness = sum(event.usefulness_score for event in self._events) / len(self._events)
        risk = sum(event.hallucination_risk_score for event in self._events) / len(self._events)
        return {"events": float(len(self._events)), "avg_usefulness": usefulness, "avg_hallucination_risk": risk}


class DurableMemoryStore:
    """JSON-backed memory store with TTL and scoped access controls."""

    def __init__(self, path: Path) -> None:
        self._path = path
        self._path.parent.mkdir(parents=True, exist_ok=True)
        if not self._path.exists():
            self._write([])

    def put(
        self,
        item: MemoryItem,
        *,
        ttl_seconds: int | None = None,
    ) -> MemoryItem:
        self._assert_scope_constraints(item)
        if ttl_seconds is not None:
            item.expires_at = (_utc_now() + timedelta(seconds=ttl_seconds)).isoformat()
        item.updated_at = _utc_now().isoformat()
        rows = self._read()
        rows = [row for row in rows if row["item_id"] != item.item_id]
        rows.append(asdict(item))
        self._write(rows)
        return item

    def edit(
        self,
        item_id: str,
        context: MemoryRequestContext,
        *,
        content: str | None = None,
        tone: str | None = None,
        output_format: str | None = None,
        recurring_goal: str | None = None,
        ttl_seconds: int | None = None,
    ) -> MemoryItem:
        item = self._find_editable(item_id, context)
        if content is not None:
            item.content = content
        if tone is not None:
            item.tone = tone
        if output_format is not None:
            item.output_format = output_format
        if recurring_goal is not None:
            item.recurring_goal = recurring_goal
        if ttl_seconds is not None:
            item.expires_at = (_utc_now() + timedelta(seconds=ttl_seconds)).isoformat()
        item.updated_at = _utc_now().isoformat()
        return self.put(item)

    def delete(self, item_id: str, context: MemoryRequestContext) -> bool:
        item = self._find_editable(item_id, context)
        rows = self._read()
        updated = [row for row in rows if row["item_id"] != item.item_id]
        self._write(updated)
        return len(updated) != len(rows)

    def purge_expired(self) -> int:
        now = _utc_now()
        rows = self._read()
        kept = [row for row in rows if not MemoryItem(**row).is_expired(now)]
        removed = len(rows) - len(kept)
        if removed:
            self._write(kept)
        return removed

    def query(self, context: MemoryRequestContext) -> list[MemoryItem]:
        self.purge_expired()
        results = []
        for row in self._read():
            item = MemoryItem(**row)
            if self._can_read(item, context):
                results.append(item)
        return results

    def _find_editable(self, item_id: str, context: MemoryRequestContext) -> MemoryItem:
        for row in self._read():
            item = MemoryItem(**row)
            if item.item_id == item_id and self._can_edit(item, context):
                return item
        raise PermissionError("memory item not found or not editable in request context")

    def _can_read(self, item: MemoryItem, context: MemoryRequestContext) -> bool:
        if item.tenant_id != context.tenant_id:
            return False
        if item.scope == MEMORY_SCOPE_SESSION:
            return item.user_id == context.user_id and item.session_id == context.session_id
        if item.scope == MEMORY_SCOPE_USER:
            return item.user_id == context.user_id
        if item.scope == MEMORY_SCOPE_TEAM:
            return item.team_id is not None and item.team_id == context.team_id
        if item.scope == MEMORY_SCOPE_GLOBAL:
            return True
        return False

    def _can_edit(self, item: MemoryItem, context: MemoryRequestContext) -> bool:
        if not self._can_read(item, context):
            return False
        if item.scope == MEMORY_SCOPE_GLOBAL:
            return context.user_id == "admin"
        return True

    def _assert_scope_constraints(self, item: MemoryItem) -> None:
        if item.scope == MEMORY_SCOPE_SESSION and (item.user_id is None or item.session_id is None):
            raise ValueError("session scope requires user_id and session_id")
        if item.scope == MEMORY_SCOPE_USER and item.user_id is None:
            raise ValueError("user scope requires user_id")
        if item.scope == MEMORY_SCOPE_TEAM and item.team_id is None:
            raise ValueError("team scope requires team_id")

    def _read(self) -> list[dict[str, Any]]:
        with self._path.open("r", encoding="utf-8") as handle:
            return json.load(handle)

    def _write(self, payload: list[dict[str, Any]]) -> None:
        with self._path.open("w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2, sort_keys=True)


class ScopedMemoryRetriever:
    """Applies scope and relevance rules before memory injection."""

    def __init__(self, store: DurableMemoryStore) -> None:
        self._store = store

    def retrieve(self, query: str, context: MemoryRequestContext, *, top_k: int = 6) -> MemoryInjection:
        candidates = self._store.query(context)
        ordered = sorted(
            candidates,
            key=lambda item: (self._scope_rank(item.scope), self._relevance(query, item.content), item.updated_at),
            reverse=True,
        )
        selected = ordered[:top_k]
        return MemoryInjection(request_context=context, query=query, items=tuple(selected))

    @staticmethod
    def _scope_rank(scope: MemoryScope) -> int:
        order = {
            MEMORY_SCOPE_SESSION: 4,
            MEMORY_SCOPE_USER: 3,
            MEMORY_SCOPE_TEAM: 2,
            MEMORY_SCOPE_GLOBAL: 1,
        }
        return order.get(scope, 0)

    @staticmethod
    def _relevance(query: str, content: str) -> int:
        query_tokens = {token for token in query.lower().split() if token}
        content_tokens = {token for token in content.lower().split() if token}
        return len(query_tokens & content_tokens)


__all__ = [
    "DurableMemoryStore",
    "MEMORY_SCOPE_GLOBAL",
    "MEMORY_SCOPE_SESSION",
    "MEMORY_SCOPE_TEAM",
    "MEMORY_SCOPE_USER",
    "MemoryInjection",
    "MemoryItem",
    "MemoryRequestContext",
    "MemoryTelemetry",
    "MemoryTelemetryEvent",
    "ScopedMemoryRetriever",
]
