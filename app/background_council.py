from __future__ import annotations

import json
import subprocess
import threading
import time
from datetime import UTC, datetime
from typing import Any, Callable

from .git_publisher import GitPublisher
from .storage import (
    KNOWLEDGE_FILE,
    append_json_row,
    load_autopilot_snapshot,
    load_council_snapshot,
    load_intelligence_snapshot,
    load_proposals_snapshot,
    load_workspace_learning_snapshot,
    save_council_snapshot,
)


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
            context = self._build_context_bundle()
            signature = json.dumps(
                {
                    "entries": git_status.get("entries", []),
                    "branch": git_status.get("branch"),
                    "intelligence_run_at": context["intelligence"].get("last_run_at"),
                    "learning_run_at": context["workspace_learning"].get("last_run_at"),
                    "proposal_updated_at": (context["proposals"].get("active_proposal") or {}).get("updated_at"),
                    "autopilot_run_at": context["autopilot"].get("last_run_at"),
                    "learning_sources": [item.get("source_path") for item in context["workspace_learning"].get("recent_items", [])[:4]],
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
            if not manual and not git_status.get("has_changes") and not self._has_advisory_inputs(context):
                snapshot["state"] = "idle"
                snapshot["git"] = git_status
                snapshot["context"] = context
                snapshot["last_signature"] = signature
                save_council_snapshot(snapshot)
                return snapshot

            snapshot["state"] = "testing"
            snapshot["git"] = git_status
            snapshot["context"] = context
            save_council_snapshot(snapshot)

            test_result = self._run_tests(str(settings.get("test_command") or 'py -m unittest discover -s tests -p "test_*.py"'))
            snapshot["state"] = "deliberating"
            snapshot["tests"] = test_result

            votes, transcript = self._deliberate(
                model=str(settings.get("model") or "qwen2.5:7b-instruct-q4_K_M"),
                git_status=git_status,
                test_result=test_result,
                context=context,
            )
            decision = self._decide(
                votes=votes,
                test_result=test_result,
                threshold=int(settings.get("auto_approve_threshold", 2) or 2),
                auto_commit_enabled=bool(settings.get("auto_commit_enabled", False)),
                has_changes=bool(git_status.get("has_changes")),
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
                    "context": context,
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
                    "summary": f"Council {'approved' if decision['approved'] else 'reviewed'} {'worktree changes' if git_status.get('has_changes') else 'background learning insights'} with {decision['approve_votes']} approve vote(s) and tests {'passing' if test_result['success'] else 'failing'}.",
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

    def _build_context_bundle(self) -> dict[str, Any]:
        intelligence = load_intelligence_snapshot()
        workspace_learning = load_workspace_learning_snapshot()
        return {
            "intelligence": intelligence,
            "workspace_learning": workspace_learning,
            "proposals": load_proposals_snapshot(),
            "autopilot": load_autopilot_snapshot(),
        }

    def _has_advisory_inputs(self, context: dict[str, Any]) -> bool:
        intelligence = context.get("intelligence", {})
        workspace_learning = context.get("workspace_learning", {})
        proposals = context.get("proposals", {})
        autopilot = context.get("autopilot", {})
        return bool(
            intelligence.get("focus_areas")
            or intelligence.get("suggested_tasks")
            or workspace_learning.get("recent_items")
            or proposals.get("active_proposal")
            or autopilot.get("execution")
        )

    def _format_context_digest(self, context: dict[str, Any]) -> str:
        intelligence = context.get("intelligence", {})
        workspace_learning = context.get("workspace_learning", {})
        proposals = context.get("proposals", {})
        autopilot = context.get("autopilot", {})
        focus_areas = intelligence.get("focus_areas", [])[:3]
        tasks = intelligence.get("suggested_tasks", [])[:3]
        learned_items = workspace_learning.get("recent_items", [])[:4]
        active_proposal = proposals.get("active_proposal") or {}
        autopilot_safety = autopilot.get("safety", {})
        autopilot_execution = autopilot.get("execution", {})

        focus_lines = [
            f"- {item.get('title', 'Focus area')}: {item.get('reason', 'No reason provided.')}"
            for item in focus_areas
        ]
        task_lines = [
            f"- {item.get('title', 'Suggested task')}: {item.get('reason', 'No reason provided.')}"
            for item in tasks
        ]
        learned_lines = [
            f"- {item.get('source_path', 'workspace file')}: {item.get('conclusion', item.get('summary', 'No conclusion provided.'))}"
            for item in learned_items
        ]
        return (
            f"Background intelligence last run: {intelligence.get('last_run_at') or 'never'}\n"
            f"Background workspace learning last run: {workspace_learning.get('last_run_at') or 'never'}\n"
            f"Autopilot last run: {autopilot.get('last_run_at') or 'never'}\n"
            "Focus areas:\n"
            f"{chr(10).join(focus_lines) if focus_lines else '- None'}\n\n"
            "Suggested tasks:\n"
            f"{chr(10).join(task_lines) if task_lines else '- None'}\n\n"
            "Learned file conclusions:\n"
            f"{chr(10).join(learned_lines) if learned_lines else '- None'}\n\n"
            "Active proposal:\n"
            f"- Title: {active_proposal.get('title', 'None')}\n"
            f"- Targets: {', '.join(active_proposal.get('target_files', [])) or 'None'}\n"
            f"- Problem: {active_proposal.get('suspected_problem', 'None')}\n"
            f"- Change: {active_proposal.get('suggested_change', 'None')}\n"
            f"- Expected test impact: {active_proposal.get('expected_test_impact', 'None')}\n\n"
            "Autopilot execution:\n"
            f"- Selected tests: {', '.join(autopilot_execution.get('tests', {}).get('selected_tests', [])) or 'None'}\n"
            f"- Safety approved: {autopilot_safety.get('approved')}\n"
            f"- Safety reasons: {', '.join(autopilot_safety.get('reasons', [])) or 'None'}\n"
            f"- Changed paths: {', '.join(autopilot_safety.get('changed_paths', [])) or 'None'}"
        )

    def _deliberate(
        self,
        *,
        model: str,
        git_status: dict[str, Any],
        test_result: dict[str, Any],
        context: dict[str, Any],
    ) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
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
        context_digest = self._format_context_digest(context)
        work_mode = "code-change review" if git_status.get("has_changes") else "advisory improvement review"

        for name, role in personas:
            prompt = (
                f"{role}\n"
                "Return strict JSON with keys: vote, confidence, rationale, commit_message.\n"
                "vote must be one of approve, revise, reject.\n"
                f"Current council mode: {work_mode}\n"
                f"Tests passed: {test_result.get('success')}\n"
                f"Test command: {test_result.get('command')}\n"
                f"Git branch: {git_status.get('branch')}\n"
                f"Git status:\n{diff_lines}\n\n"
                f"Diff stat:\n{diff_stat}\n\n"
                f"Test stdout tail:\n{stdout_tail or 'none'}\n\n"
                f"Test stderr tail:\n{stderr_tail or 'none'}\n\n"
                f"Background improvement context:\n{context_digest}\n\n"
                "Use the background improvement context when deciding what should happen next."
                " If there are no worktree changes, vote based on whether the inferred next steps are strong enough to pursue next,"
                " but do not assume a commit should happen without code changes."
            )
            try:
                raw = self.generate_text(model, prompt)
                parsed = self._parse_vote(raw)
            except Exception as exc:
                parsed = self._fallback_vote(name=name, git_status=git_status, test_result=test_result, reason=str(exc))
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

    def _fallback_vote(self, *, name: str, git_status: dict[str, Any], test_result: dict[str, Any], reason: str) -> dict[str, Any]:
        tests_passed = bool(test_result.get("success"))
        has_changes = bool(git_status.get("has_changes"))
        branch = str(git_status.get("branch", "unknown"))
        if not has_changes:
            vote = "revise"
            rationale = f"{name} fallback review: there are no current worktree changes to approve on branch {branch}. Model call failed with: {reason}"
        elif tests_passed:
            vote = "approve"
            rationale = f"{name} fallback review: tests passed and the worktree has changes, so this looks shippable pending deeper model review. Model call failed with: {reason}"
        else:
            vote = "revise"
            rationale = f"{name} fallback review: tests failed, so the worktree should not auto-ship yet. Model call failed with: {reason}"
        return {
            "vote": vote,
            "confidence": 0.35 if tests_passed else 0.2,
            "rationale": rationale[:800],
            "commit_message": "Ship reviewed worktree changes",
        }

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

    def _decide(
        self,
        *,
        votes: list[dict[str, Any]],
        test_result: dict[str, Any],
        threshold: int,
        auto_commit_enabled: bool,
        has_changes: bool,
    ) -> dict[str, Any]:
        approve_votes = sum(1 for item in votes if item.get("vote") == "approve")
        reject_votes = sum(1 for item in votes if item.get("vote") == "reject")
        revise_votes = sum(1 for item in votes if item.get("vote") == "revise")
        tests_passed = bool(test_result.get("success"))
        approved = has_changes and tests_passed and approve_votes >= threshold and approve_votes > reject_votes
        if approved:
            rationale = "Tests passed and the council majority approved the change."
        elif not has_changes:
            rationale = "The council reviewed fresh learning and inference context, but there are no worktree changes to auto-approve yet."
        else:
            rationale = "The council did not find enough confidence to auto-approve the current worktree."
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
