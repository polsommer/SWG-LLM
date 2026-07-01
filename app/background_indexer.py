from __future__ import annotations

import threading
from datetime import datetime, UTC
from typing import Any

from .indexer import ProjectIndexer


class BackgroundIndexer:
    def __init__(self, indexer: ProjectIndexer, poll_seconds: int = 8) -> None:
        self.indexer = indexer
        self.poll_seconds = poll_seconds
        self._thread: threading.Thread | None = None
        self._stop_event = threading.Event()
        self._status_lock = threading.Lock()
        self._last_signature: list[dict] | None = None
        self._status: dict[str, Any] = {
            "enabled": True,
            "state": "idle",
            "poll_seconds": poll_seconds,
            "last_scan_at": None,
            "last_change_at": None,
            "last_reindex_at": None,
            "last_error": None,
        }

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._run_loop, name="local-agent-background-indexer", daemon=True)
        self._thread.start()

    def get_status(self) -> dict[str, Any]:
        with self._status_lock:
            status = dict(self._status)
        index_status = self.indexer.get_status()
        status["index"] = {
            "indexed_at": index_status.get("indexed_at"),
            "file_count": index_status.get("file_count", 0),
            "chunk_count": index_status.get("chunk_count", 0),
        }
        return status

    def _set_status(self, **updates: Any) -> None:
        with self._status_lock:
            self._status.update(updates)

    def reset_signature(self) -> None:
        self._last_signature = None
        self._set_status(last_change_at=datetime.now(UTC).isoformat())

    def _run_loop(self) -> None:
        while not self._stop_event.is_set():
            now = datetime.now(UTC).isoformat()
            self._set_status(last_scan_at=now, state="scanning")
            try:
                signature = self.indexer.build_file_signature()
                index_status = self.indexer.get_status()
                should_index = False

                if self._last_signature is None:
                    self._last_signature = signature
                    if signature and index_status.get("file_count", 0) == 0:
                        should_index = True
                elif signature != self._last_signature:
                    self._last_signature = signature
                    self._set_status(last_change_at=now)
                    should_index = True

                if should_index:
                    self._set_status(state="reindexing", last_error=None)
                    result = self.indexer.index_project()
                    self._set_status(last_reindex_at=result.get("indexed_at"))

                self._set_status(state="idle", last_error=None)
            except Exception as exc:
                self._set_status(state="error", last_error=str(exc))

            self._stop_event.wait(self.poll_seconds)
