from __future__ import annotations

import json
import subprocess
import threading
import time
from datetime import UTC, datetime
from typing import Any, Callable

from .git_publisher import GitPublisher
from .storage import append_json_row, KNOWLEDGE_FILE, load_council_snapshot, save_council_snapshot


class BackgroundCouncil:
    def __init__(
        self,
        generate_text: Callable[[str, str], str],
        git_publisher: GitPublisher | None = None,
    ) -> None:
        self.generate_text = generate_text
        self.git_publisher = git_publisher or GitPublisher()
        self._thread: threading.Thread | None = None
        self._stop_event = threading.Event()
        self._lock = threading.Lock()

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._run_loop, name="local-agent-background-council", daemon=True)
        self._thread.start()

    def get_status(self) -> dict[str, Any]:
        with self._lock:
            return load_council_snapshot()

    def update_settings(self, updates: dict[str, Any]) -> dict[str, Any]:
        with self._lock:
            snapshot = load_council_snapshot()
            settings = dict(snapshot.get("settings", {}))
            settings.update(updates)
            snapshot["settings"] = settings
            save_council_snapshot(snapshot)
            return snapshot

    def run_once(self, manual: bool = False) -> dict[str, Any]:
        with self._lock:
            snapshot = load_council_snapshot()
            settings = dict(snapshot.get("settings", {}))
            git_status = self.git_publisher.worktree_status()
            signature = json.dumps(
                {
                    "entries": git_status.get("entries", []),
                    "branch": git_status.get("branch"),
                },
                ensure_ascii=True,
            )
            if not manual and not settings.get("enabled", True):
                snapshot["state"] = "disabled"
                save_council_snapshot(snapshot)
                return snapshot
            if not manual and signature == snapshot.get("last_signature", ""):
                snapshot["state"] = "idle"
                save_council_snapshot(snapshot)
                return snapshot
            if not manual and not git_status.get("has_changes"):
                snapshot["state"] = "idle"
                snapshot["git"] = git_status
                snapshot["last_signature"] = signature
                save_council_snapshot(snapshot)
                return snapshot

            snapshot["state"] = "testing"
            snapshot["git"] = git_status
            save_council_snapshot(snapshot)

            test_result = self._run_tests(str(settings.get("test_command") or 'py -m unittest discover -s tests -p "test_*.py"'))
            snapshot["state"] = "deliberating"
            snapshot["tests"] = test_result

            votes, transcript = self._deliberate(
                model=str(settings.get("model") or "qwen2.5:7b-instruct-q4_K_M"),
                git_status=git_status,
                test_result=test_result,
            )
            decision = self._decide(
                votes=votes,
                test_result=test_result,
                threshold=int(settings.get("auto_approve_threshold", 2) or 2),
                auto_commit_enabled=bool(settings.get("auto_commit_enabled", False)),
            )

            git_publish = None
            if decision["approved"] and settings.get("auto_commit_enabled", False):
                commit_message = decision["commit_message"]
                git_publish = self.git_publisher.publish_worktree(
                    commit_message=commit_message,
                    push_to_remote=bool(settings.get("auto_push_enabled", False)),
                )
                transcript.append(
                    {
                        "speaker": "Publisher",
                        "role": "publisher",
                        "vote": "publish",
                        "message": f"Published approved worktree changes on branch {git_publish.get('branch', 'unknown')}.",
                    }
                )

            now = datetime.now(UTC).isoformat()
            snapshot.update(
                {
                    "state": "ready",
                    "last_run_at": now,
                    "last_signature": signature,
                    "last_error": None,
                    "git": git_status,
                    "tests": test_result,
                    "votes": votes,
                    "decision": {
                        **decision,
                        "git_publish": git_publish,
                    },
                    "transcript": transcript,
                }
            )
            save_council_snapshot(snapshot)
            append_json_row(
                KNOWLEDGE_FILE,
                {
                    "timestamp": now,
                    "session_id": "background-council",
                    "kind": "council_decision",
                    "summary": f"Council {'approved' if decision['approved'] else 'rejected'} a worktree review with {decision['approve_votes']} approve vote(s) and tests {'passing' if test_result['success'] else 'failing'}.",
                },
            )
            return snapshot

    def _run_loop(self) -> None:
        while not self._stop_event.is_set():
            try:
                snapshot = load_council_snapshot()
                poll_seconds = int(snapshot.get("settings", {}).get("poll_seconds", 45) or 45)
                self.run_once(manual=False)
            except Exception as exc:
                with self._lock:
                    snapshot = load_council_snapshot()
                    snapshot["state"] = "error"
                    snapshot["last_error"] = str(exc)
                    save_council_snapshot(snapshot)
                poll_seconds = 45
            self._stop_event.wait(poll_seconds)

    def _run_tests(self, command: str) -> dict[str, Any]:
        started = time.perf_counter()
        completed = subprocess.run(
            command,
            cwd=self.git_publisher.repo_root,
            capture_output=True,
            text=True,
            timeout=300,
            shell=True,
        )
        duration = round(time.perf_counter() - started, 2)
        stdout = (completed.stdout or "").strip()
        stderr = (completed.stderr or "").strip()
        return {
            "command": command,
            "success": completed.returncode == 0,
            "return_code": completed.returncode,
            "duration_seconds": duration,
            "stdout_tail": stdout[-4000:],
            "stderr_tail": stderr[-4000:],
        }

    def _deliberate(self, *, model: str, git_status: dict[str, Any], test_result: dict[str, Any]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
        personas = [
            ("Reviewer", "You review changes for correctness and maintainability."),
            ("Skeptic", "You look for hidden regressions, weak evidence, and reasons not to ship."),
            ("Captain", "You decide whether the change is ready to ship if tests and evidence are good enough."),
        ]
        votes: list[dict[str, Any]] = []
        transcript: list[dict[str, Any]] = []
        diff_lines = "\n".join(git_status.get("entries", [])[:20]) or "No git status entries."
        diff_stat = str(git_status.get("diff_stat", "")).strip() or "No diff stat available."
        stdout_tail = str(test_result.get("stdout_tail", "")).strip()[-1800:]
        stderr_tail = str(test_result.get("stderr_tail", "")).strip()[-1200:]

        for name, role in personas:
            prompt = (
                f"{role}\n"
                "Return strict JSON with keys: vote, confidence, rationale, commit_message.\n"
                "vote must be one of approve, revise, reject.\n"
                f"Tests passed: {test_result.get('success')}\n"
                f"Test command: {test_result.get('command')}\n"
                f"Git branch: {git_status.get('branch')}\n"
                f"Git status:\n{diff_lines}\n\n"
                f"Diff stat:\n{diff_stat}\n\n"
                f"Test stdout tail:\n{stdout_tail or 'none'}\n\n"
                f"Test stderr tail:\n{stderr_tail or 'none'}\n\n"
                "Decide whether this worktree should be auto-approved for commit and explain why."
            )
            raw = self.generate_text(model, prompt)
            parsed = self._parse_vote(raw)
            votes.append({"speaker": name, **parsed})
            transcript.append(
                {
                    "speaker": name,
                    "role": name.lower(),
                    "vote": parsed["vote"],
                    "message": parsed["rationale"],
                    "confidence": parsed["confidence"],
                }
            )
        return votes, transcript

    def _parse_vote(self, raw: str) -> dict[str, Any]:
        try:
            parsed = json.loads(raw)
            if isinstance(parsed, dict):
                vote = str(parsed.get("vote", "revise")).strip().lower()
                if vote not in {"approve", "revise", "reject"}:
                    vote = "revise"
                confidence = float(parsed.get("confidence", 0.5))
                rationale = str(parsed.get("rationale", raw[:400])).strip() or raw[:400]
                commit_message = str(parsed.get("commit_message", "Ship reviewed worktree changes")).strip() or "Ship reviewed worktree changes"
                return {
                    "vote": vote,
                    "confidence": max(0.0, min(1.0, confidence)),
                    "rationale": rationale[:800],
                    "commit_message": commit_message[:120],
                }
        except json.JSONDecodeError:
            pass
        return {
            "vote": "revise",
            "confidence": 0.3,
            "rationale": raw.strip()[:800] or "Council member did not return parseable output.",
            "commit_message": "Ship reviewed worktree changes",
        }

    def _decide(self, *, votes: list[dict[str, Any]], test_result: dict[str, Any], threshold: int, auto_commit_enabled: bool) -> dict[str, Any]:
        approve_votes = sum(1 for item in votes if item.get("vote") == "approve")
        reject_votes = sum(1 for item in votes if item.get("vote") == "reject")
        revise_votes = sum(1 for item in votes if item.get("vote") == "revise")
        tests_passed = bool(test_result.get("success"))
        approved = tests_passed and approve_votes >= threshold and approve_votes > reject_votes
        rationale = "Tests passed and the council majority approved the change." if approved else "The council did not find enough confidence to auto-approve the current worktree."
        winning_message = next((item.get("commit_message") for item in votes if item.get("vote") == "approve" and item.get("commit_message")), None)
        commit_message = winning_message or "Ship reviewed worktree changes"
        return {
            "approved": approved,
            "approve_votes": approve_votes,
            "reject_votes": reject_votes,
            "revise_votes": revise_votes,
            "tests_passed": tests_passed,
            "auto_commit_attempted": approved and auto_commit_enabled,
            "rationale": rationale,
            "commit_message": commit_message,
        }
