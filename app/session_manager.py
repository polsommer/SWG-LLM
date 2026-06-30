from __future__ import annotations

import threading
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Iterator


@dataclass
class SessionState:
    session_id: str
    created_at: str
    last_seen_at: str
    request_count: int = 0
    active_requests: int = 0
    approval_count: int = 0
    parse_failure_count: int = 0
    execution_denied_count: int = 0
    execution_error_count: int = 0
    last_error: str | None = None
    _lock: threading.RLock = field(default_factory=threading.RLock, repr=False)


class SessionManager:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._sessions: dict[str, SessionState] = {}

    def _now(self) -> str:
        return datetime.now(UTC).isoformat()

    def get_or_create(self, session_id: str) -> SessionState:
        with self._lock:
            state = self._sessions.get(session_id)
            if state is None:
                now = self._now()
                state = SessionState(session_id=session_id, created_at=now, last_seen_at=now)
                self._sessions[session_id] = state
            else:
                state.last_seen_at = self._now()
            return state

    @contextmanager
    def request_scope(self, session_id: str) -> Iterator[SessionState]:
        state = self.get_or_create(session_id)
        with state._lock:
            state.last_seen_at = self._now()
            state.request_count += 1
            state.active_requests += 1
            try:
                yield state
            finally:
                state.active_requests = max(0, state.active_requests - 1)
                state.last_seen_at = self._now()

    def record_approval(self, session_id: str) -> SessionState:
        state = self.get_or_create(session_id)
        with state._lock:
            state.approval_count += 1
            state.last_seen_at = self._now()
        return state

    def record_parse_failure(self, session_id: str, reason: str) -> SessionState:
        state = self.get_or_create(session_id)
        with state._lock:
            state.parse_failure_count += 1
            state.last_error = reason
            state.last_seen_at = self._now()
        return state

    def record_execution_denied(self, session_id: str, reason: str) -> SessionState:
        state = self.get_or_create(session_id)
        with state._lock:
            state.execution_denied_count += 1
            state.last_error = reason
            state.last_seen_at = self._now()
        return state

    def record_execution_error(self, session_id: str, reason: str) -> SessionState:
        state = self.get_or_create(session_id)
        with state._lock:
            state.execution_error_count += 1
            state.last_error = reason
            state.last_seen_at = self._now()
        return state

    def snapshot(self, session_id: str) -> dict[str, str | int | None]:
        state = self.get_or_create(session_id)
        with state._lock:
            return {
                "session_id": state.session_id,
                "created_at": state.created_at,
                "last_seen_at": state.last_seen_at,
                "request_count": state.request_count,
                "active_requests": state.active_requests,
                "approval_count": state.approval_count,
                "parse_failure_count": state.parse_failure_count,
                "execution_denied_count": state.execution_denied_count,
                "execution_error_count": state.execution_error_count,
                "last_error": state.last_error,
            }
