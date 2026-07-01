from __future__ import annotations

import json
import threading
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Callable

from .storage import (
    GENERATED_DIR,
    KNOWLEDGE_FILE,
    UPLOADS_DIR,
    append_json_row,
    list_text_files,
    load_workspace_learning_snapshot,
    read_text_file,
    save_generated_file,
    save_workspace_learning_snapshot,
)


class BackgroundWorkspaceLearning:
    def __init__(self, generate_text: Callable[[str, str], str], poll_seconds: int = 25) -> None:
        self.generate_text = generate_text
        self.poll_seconds = poll_seconds
        self._thread: threading.Thread | None = None
        self._stop_event = threading.Event()
        self._lock = threading.Lock()

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._run_loop, name="local-agent-background-workspace-learning", daemon=True)
        self._thread.start()

    def get_status(self) -> dict[str, Any]:
        with self._lock:
            return load_workspace_learning_snapshot()

    def _safe_artifact_name(self, relative_path: str) -> str:
        return relative_path.replace("/", "__").replace("\\", "__")

    def _tracked_files(self) -> list[tuple[str, Path]]:
        paths: list[tuple[str, Path]] = []
        for root_name, root in (("uploads", UPLOADS_DIR), ("generated", GENERATED_DIR)):
            for path in list_text_files(root):
                relative = path.relative_to(root).as_posix()
                if root_name == "generated" and relative.startswith("learned/"):
                    continue
                paths.append((f"{root_name}/{relative}", path))
        return sorted(paths, key=lambda item: item[0])

    def _signature(self, items: list[tuple[str, Path]]) -> str:
        rows = []
        for relative, path in items:
            try:
                stat = path.stat()
            except OSError:
                continue
            rows.append({"path": relative, "size": stat.st_size, "mtime": stat.st_mtime})
        return json.dumps(rows, ensure_ascii=True)

    def _fallback_learning(self, relative_path: str, text: str) -> dict[str, Any]:
        lines = [line.strip() for line in text.splitlines() if line.strip()]
        summary = lines[0][:220] if lines else "No meaningful content was extracted."
        concern = lines[1][:220] if len(lines) > 1 else "The file may need deeper inspection to validate assumptions."
        conclusion = f"This file appears worth keeping in reusable workspace context because it contains actionable text from {relative_path}."
        return {
            "summary": summary,
            "supporting_view": f"The file provides concrete workspace material from {relative_path}.",
            "skeptical_view": concern,
            "conclusion": conclusion,
            "next_actions": [
                f"Ask the workspace to relate {relative_path} to current SWG repo questions.",
                "Promote the file into a generated note if it should drive future edits.",
            ],
        }

    def _learn_file(self, model: str, relative_path: str, path: Path) -> dict[str, Any]:
        text = read_text_file(path, max_chars=7000)
        if not text.strip():
            result = self._fallback_learning(relative_path, text)
        else:
            prompt = (
                "You are part of an automatic file-learning pipeline for a SWG workspace.\n"
                "Read the file excerpt and return strict JSON with keys: summary, supporting_view, skeptical_view, conclusion, next_actions.\n"
                "next_actions must be an array of short strings.\n"
                "Make this useful for future repo work and debate.\n\n"
                f"File: {relative_path}\n\n"
                f"Excerpt:\n{text}"
            )
            try:
                raw = self.generate_text(model, prompt)
                parsed = json.loads(raw)
                if not isinstance(parsed, dict):
                    raise ValueError("Model output was not a JSON object")
                result = {
                    "summary": str(parsed.get("summary", "")).strip()[:240] or f"Learned context from {relative_path}.",
                    "supporting_view": str(parsed.get("supporting_view", "")).strip()[:400],
                    "skeptical_view": str(parsed.get("skeptical_view", "")).strip()[:400],
                    "conclusion": str(parsed.get("conclusion", "")).strip()[:500] or f"The file {relative_path} should stay in learned workspace context.",
                    "next_actions": [str(item).strip()[:180] for item in parsed.get("next_actions", []) if str(item).strip()][:4],
                }
            except Exception:
                result = self._fallback_learning(relative_path, text)

        artifact_path = f"learned/{self._safe_artifact_name(relative_path)}.md"
        artifact_body = "\n".join(
            [
                f"# Learned File Review",
                "",
                f"- Source: `{relative_path}`",
                "",
                "## Summary",
                result["summary"],
                "",
                "## Supporting View",
                result["supporting_view"],
                "",
                "## Skeptical View",
                result["skeptical_view"],
                "",
                "## Conclusion",
                result["conclusion"],
                "",
                "## Next Actions",
                *[f"- {item}" for item in result["next_actions"]],
            ]
        )
        save_generated_file(artifact_path, artifact_body)
        return {
            "source_path": relative_path,
            "artifact_path": f"generated/{artifact_path}",
            **result,
        }

    def _run_once(self) -> dict[str, Any]:
        snapshot = load_workspace_learning_snapshot()
        settings = dict(snapshot.get("settings", {}))
        files = self._tracked_files()
        signature = self._signature(files)

        if not settings.get("enabled", True):
            snapshot["state"] = "disabled"
            return save_workspace_learning_snapshot(snapshot)

        if signature == snapshot.get("last_signature", "") and snapshot.get("recent_items"):
            snapshot["state"] = "idle"
            return save_workspace_learning_snapshot(snapshot)

        model = str(settings.get("model") or "qwen2.5:7b-instruct-q4_K_M")
        recent_items: list[dict[str, Any]] = []
        for relative_path, path in files[-8:]:
            recent_items.append(self._learn_file(model, relative_path, path))

        now = datetime.now(UTC).isoformat()
        snapshot.update(
            {
                "state": "ready",
                "last_run_at": now,
                "last_signature": signature,
                "last_error": None,
                "recent_items": recent_items[-8:],
            }
        )
        save_workspace_learning_snapshot(snapshot)

        for item in recent_items[-4:]:
            append_json_row(
                KNOWLEDGE_FILE,
                {
                    "timestamp": now,
                    "session_id": "background-workspace-learning",
                    "kind": "learned_file",
                    "summary": str(item.get("conclusion", ""))[:220],
                },
            )
        return snapshot

    def _run_loop(self) -> None:
        while not self._stop_event.is_set():
            with self._lock:
                try:
                    snapshot = load_workspace_learning_snapshot()
                    self._run_once()
                    poll_seconds = int(snapshot.get("settings", {}).get("poll_seconds", self.poll_seconds) or self.poll_seconds)
                except Exception as exc:
                    snapshot = load_workspace_learning_snapshot()
                    snapshot["state"] = "error"
                    snapshot["last_error"] = str(exc)
                    save_workspace_learning_snapshot(snapshot)
                    poll_seconds = self.poll_seconds
            self._stop_event.wait(poll_seconds)
