from __future__ import annotations

import threading
import uuid
from dataclasses import dataclass
from typing import Any


@dataclass
class PendingApproval:
    approval_id: str
    model: str
    prompt: str
    raw_reply: str
    tool_name: str
    arguments: dict[str, Any]
    created_files: list[str]
    tool_events: list[str]
    next_step: int
    reason: str


class ApprovalManager:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._pending_by_session: dict[str, PendingApproval] = {}

    def describe_reason(self, tool_name: str, arguments: dict[str, Any]) -> str:
        if tool_name == "write_file":
            return f"The agent wants to write a generated file at `generated/{arguments.get('path', '')}`."
        if tool_name == "run_python":
            return "The agent wants to run code in the guarded local Python runner. This is best-effort isolation, not a hardened sandbox."
        if tool_name == "run_python_script":
            return (
                f"The agent wants to run the Python script `{arguments.get('path', '')}` in the guarded local Python runner. "
                "This is best-effort isolation, not a hardened sandbox."
            )
        return f"The agent wants to run `{tool_name}`."

    def requires_approval(self, tool_name: str) -> bool:
        return tool_name in {"write_file", "run_python", "run_python_script"}

    def create(
        self,
        *,
        model: str,
        prompt: str,
        raw_reply: str,
        tool_name: str,
        arguments: dict[str, Any],
        created_files: list[str],
        tool_events: list[str],
        next_step: int,
        session_id: str,
    ) -> dict[str, Any]:
        pending = PendingApproval(
            approval_id=str(uuid.uuid4()),
            model=model,
            prompt=prompt,
            raw_reply=raw_reply,
            tool_name=tool_name,
            arguments=arguments,
            created_files=list(created_files),
            tool_events=list(tool_events),
            next_step=next_step,
            reason=self.describe_reason(tool_name, arguments),
        )
        with self._lock:
            self._pending_by_session[session_id] = pending
        return self.current(session_id)

    def current(self, session_id: str) -> dict[str, Any] | None:
        with self._lock:
            pending = self._pending_by_session.get(session_id)
            if pending is None:
                return None
        return {
            "approval_id": pending.approval_id,
            "tool_name": pending.tool_name,
            "arguments": pending.arguments,
            "reason": pending.reason,
        }

    def take(self, session_id: str) -> PendingApproval | None:
        with self._lock:
            pending = self._pending_by_session.pop(session_id, None)
        return pending

    def clear(self, session_id: str) -> None:
        with self._lock:
            self._pending_by_session.pop(session_id, None)
