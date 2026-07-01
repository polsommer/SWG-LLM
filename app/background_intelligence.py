from __future__ import annotations

import threading
from datetime import UTC, datetime
from typing import Any

from .storage import KNOWLEDGE_FILE, append_json_row, load_intelligence_snapshot, save_intelligence_snapshot


class BackgroundIntelligence:
    def __init__(self, indexer: Any, poll_seconds: int = 30) -> None:
        self.indexer = indexer
        self.poll_seconds = poll_seconds
        self._thread: threading.Thread | None = None
        self._stop_event = threading.Event()
        self._status_lock = threading.Lock()
        self._last_source_indexed_at: str | None = None
        self._status: dict[str, Any] = {
            "enabled": True,
            "state": "idle",
            "poll_seconds": poll_seconds,
            "last_scan_at": None,
            "last_run_at": None,
            "last_error": None,
        }

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        snapshot = load_intelligence_snapshot()
        self._last_source_indexed_at = str(snapshot.get("source_indexed_at") or "") or None
        self._thread = threading.Thread(target=self._run_loop, name="local-agent-background-intelligence", daemon=True)
        self._thread.start()

    def get_status(self) -> dict[str, Any]:
        with self._status_lock:
            status = dict(self._status)
        status["snapshot"] = load_intelligence_snapshot()
        return status

    def _set_status(self, **updates: Any) -> None:
        with self._status_lock:
            self._status.update(updates)

    def _infer_focus_areas(self, summary: dict[str, Any]) -> list[dict[str, str]]:
        focus_areas: list[dict[str, str]] = []
        largest_files = summary.get("largest_files", [])
        top_connected = summary.get("top_connected_symbols", [])
        top_terms = summary.get("semantic_top_terms", [])

        for item in largest_files[:3]:
            path = str(item.get("path", "")).strip()
            if not path:
                continue
            focus_areas.append(
                {
                    "title": f"Large file hotspot: {path.split('/')[-1]}",
                    "path": path,
                    "reason": "Large indexed files tend to hide broad responsibilities and are good candidates for decomposition or targeted review.",
                    "query": path.split("/")[-1],
                }
            )

        for item in top_connected[:3]:
            name = str(item.get("name", "")).strip()
            if not name:
                continue
            focus_areas.append(
                {
                    "title": f"High-traffic symbol: {name}",
                    "path": "",
                    "reason": "Cross-file graph connectivity suggests this symbol influences multiple flows and is worth tracing.",
                    "query": name,
                }
            )

        for item in top_terms[:2]:
            name = str(item.get("name", "")).strip()
            if not name:
                continue
            focus_areas.append(
                {
                    "title": f"Semantic theme: {name}",
                    "path": "",
                    "reason": "Frequent semantic concepts can point to active gameplay or infrastructure concerns in the repo.",
                    "query": name,
                }
            )
        return focus_areas[:6]

    def _infer_repo_hypotheses(self, summary: dict[str, Any]) -> list[str]:
        hypotheses: list[str] = []
        top_connected = summary.get("top_connected_symbols", [])
        top_imports = summary.get("top_imports", [])
        top_functions = summary.get("top_functions", [])

        if top_connected:
            hypotheses.append(
                f"{top_connected[0].get('name', 'A core symbol')} likely acts as a coordination point because it appears heavily connected in the cross-file graph."
            )
        if top_imports:
            hypotheses.append(
                f"Repeated dependency on {top_imports[0].get('name', 'shared includes')} suggests a reusable subsystem that may deserve a dedicated work pass."
            )
        if top_functions:
            hypotheses.append(
                f"Function patterns around {top_functions[0].get('name', 'core handlers')} may reveal a dominant execution flow worth validating with a targeted search."
            )
        return hypotheses[:4]

    def _build_suggested_tasks(self, summary: dict[str, Any], focus_areas: list[dict[str, str]]) -> list[dict[str, str]]:
        tasks: list[dict[str, str]] = []
        for index, area in enumerate(focus_areas[:4], start=1):
            tasks.append(
                {
                    "title": area["title"],
                    "priority": "high" if index <= 2 else "medium",
                    "prompt": f"Search the SWG repo for {area['query']}, inspect the strongest files, and summarize responsibilities, risks, and likely next edits.",
                    "test_prompt": f"Run a small test around {area['query']} and conclude whether it looks like a core gameplay or infrastructure hotspot.",
                    "reason": area["reason"],
                }
            )

        top_extensions = summary.get("top_extensions", [])
        if top_extensions:
            ext = str(top_extensions[0].get("name", "")).strip()
            tasks.append(
                {
                    "title": f"Audit dominant file type {ext}",
                    "priority": "medium",
                    "prompt": f"Review what responsibilities are concentrated in {ext} files and identify where repo complexity is clustering.",
                    "test_prompt": f"Run a small test that checks whether {ext} files dominate the repo enough to justify a specialized workflow.",
                    "reason": "Dominant file types often signal where tooling or indexing improvements will pay off first.",
                }
            )
        return tasks[:5]

    def _build_signals(self, status: dict[str, Any], summary: dict[str, Any]) -> list[str]:
        signals: list[str] = []
        file_count = int(status.get("file_count", 0))
        chunk_count = int(status.get("chunk_count", 0))
        truncated = int(status.get("truncated_file_count", 0))
        index_mode = str(status.get("index_mode", "deep"))
        graph_edges = int(summary.get("graph_edge_count", 0))

        signals.append(f"Indexed {file_count} files into {chunk_count} chunks using {index_mode} mode.")
        if truncated:
            signals.append(f"{truncated} files were truncated for indexing speed, which may hide details in very large sources.")
        if graph_edges:
            signals.append(f"The code graph currently tracks {graph_edges} cross-file edges, which is enough to support hotspot inference.")
        return signals[:4]

    def _build_briefing(self, status: dict[str, Any], summary: dict[str, Any]) -> list[str]:
        briefing: list[str] = []
        top_symbols = summary.get("top_symbols", [])
        largest_files = summary.get("largest_files", [])
        top_terms = summary.get("semantic_top_terms", [])

        briefing.append(
            f"The workspace background service is tracking {status.get('file_count', 0)} indexed files and {status.get('chunk_count', 0)} searchable chunks from the configured SWG roots."
        )
        if top_symbols:
            briefing.append(
                "Frequently recurring symbols right now include "
                + ", ".join(str(item.get("name", "")) for item in top_symbols[:4])
                + "."
            )
        if largest_files:
            briefing.append(
                "The largest indexed files worth reviewing first are "
                + ", ".join(str(item.get("path", "")) for item in largest_files[:3])
                + "."
            )
        if top_terms:
            briefing.append(
                "Semantic retrieval is currently orbiting concepts like "
                + ", ".join(str(item.get("name", "")) for item in top_terms[:4])
                + "."
            )
        return briefing[:4]

    def _run_analysis(self) -> dict[str, Any]:
        status = self.indexer.get_status()
        summary = status.get("summary", {})
        focus_areas = self._infer_focus_areas(summary)
        snapshot = {
            "last_run_at": datetime.now(UTC).isoformat(),
            "source_indexed_at": status.get("indexed_at"),
            "status": "ready" if status.get("file_count", 0) else "waiting_for_index",
            "briefing": self._build_briefing(status, summary),
            "focus_areas": focus_areas,
            "suggested_tasks": self._build_suggested_tasks(summary, focus_areas),
            "repo_hypotheses": self._infer_repo_hypotheses(summary),
            "signals": self._build_signals(status, summary),
        }
        save_intelligence_snapshot(snapshot)

        for line in snapshot["briefing"][:2]:
            append_json_row(
                KNOWLEDGE_FILE,
                {
                    "timestamp": snapshot["last_run_at"],
                    "session_id": "background-intelligence",
                    "kind": "background_briefing",
                    "summary": line[:220],
                },
            )
        return snapshot

    def _run_loop(self) -> None:
        while not self._stop_event.is_set():
            now = datetime.now(UTC).isoformat()
            self._set_status(last_scan_at=now, state="scanning")
            try:
                index_status = self.indexer.get_status()
                indexed_at = str(index_status.get("indexed_at") or "") or None
                should_run = False

                if indexed_at and indexed_at != self._last_source_indexed_at:
                    should_run = True
                elif load_intelligence_snapshot().get("last_run_at") is None and index_status.get("file_count", 0):
                    should_run = True

                if should_run:
                    self._set_status(state="analyzing", last_error=None)
                    snapshot = self._run_analysis()
                    self._last_source_indexed_at = indexed_at
                    self._set_status(last_run_at=snapshot.get("last_run_at"), state="idle", last_error=None)
                else:
                    self._set_status(state="idle", last_error=None)
            except Exception as exc:
                self._set_status(state="error", last_error=str(exc))

            self._stop_event.wait(self.poll_seconds)
