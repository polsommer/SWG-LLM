from __future__ import annotations

import json
import shutil
import subprocess
import threading
from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING, Any, Callable

from .storage import (
    GENERATED_DIR,
    KNOWLEDGE_FILE,
    SANDBOXES_DIR,
    append_json_row,
    load_autopilot_snapshot,
    load_proposals_snapshot,
    save_autopilot_snapshot,
    save_generated_file,
)

if TYPE_CHECKING:
    from .indexer import ProjectIndexer


class BackgroundAutopilot:
    RISKY_PREFIXES = (
        ".git/",
        ".codex/",
        ".agents/",
        "data/",
    )

    def __init__(
        self,
        generate_text: Callable[[str, str], str],
        indexer: ProjectIndexer,
        poll_seconds: int = 90,
    ) -> None:
        self.generate_text = generate_text
        self.indexer = indexer
        self.poll_seconds = poll_seconds
        self._thread: threading.Thread | None = None
        self._stop_event = threading.Event()
        self._lock = threading.Lock()

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._thread = threading.Thread(target=self._run_loop, name="local-agent-background-autopilot", daemon=True)
        self._thread.start()

    def get_status(self) -> dict[str, Any]:
        with self._lock:
            return load_autopilot_snapshot()

    def _git(self, cwd: Path, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", "-c", f"safe.directory={cwd}", *args],
            cwd=cwd,
            capture_output=True,
            text=True,
            timeout=90,
        )

    def _active_proposal(self) -> dict[str, Any] | None:
        snapshot = load_proposals_snapshot()
        proposal = snapshot.get("active_proposal")
        if isinstance(proposal, dict):
            return proposal
        rows = snapshot.get("recent_proposals", [])
        if isinstance(rows, list):
            for item in rows:
                if isinstance(item, dict):
                    return item
        return None

    def _proposal_signature(self, proposal: dict[str, Any]) -> str:
        return json.dumps(
            {
                "id": proposal.get("id"),
                "title": proposal.get("title"),
                "target_files": proposal.get("target_files", []),
                "updated_at": proposal.get("updated_at"),
            },
            ensure_ascii=True,
        )

    def _worktree_root(self, proposal_id: str) -> Path:
        safe = proposal_id.replace("/", "_").replace("\\", "_").replace(":", "_")
        return SANDBOXES_DIR / "autopilot" / safe

    def _prepare_worktree(self, repo_root: Path, proposal_id: str) -> Path:
        worktree = self._worktree_root(proposal_id)
        if worktree.exists():
            shutil.rmtree(worktree, ignore_errors=True)
        worktree.parent.mkdir(parents=True, exist_ok=True)
        completed = self._git(repo_root, "worktree", "add", "--detach", str(worktree), "HEAD")
        if completed.returncode != 0:
            raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "git worktree add failed")
        return worktree

    def _cleanup_worktree(self, repo_root: Path, worktree: Path) -> None:
        self._git(repo_root, "worktree", "remove", "--force", str(worktree))
        shutil.rmtree(worktree, ignore_errors=True)

    def _read_target_excerpt(self, target: str) -> str:
        try:
            resolved = self.indexer._resolve_project_path(target)
        except Exception:
            return ""
        try:
            return resolved.read_text(encoding="utf-8", errors="ignore")[:5000]
        except OSError:
            return ""

    def _build_plan(self, model: str, proposal: dict[str, Any]) -> dict[str, Any]:
        targets = [str(item).strip() for item in proposal.get("target_files", []) if str(item).strip()][:2]
        excerpts = []
        for target in targets:
            excerpts.append(f"FILE: {target}\n{self._read_target_excerpt(target)}")
        prompt = (
            "You are the autopilot execution planner for a SWG improvement workspace.\n"
            "Return strict JSON with keys: summary, rationale, selected_tests, file_edits.\n"
            "selected_tests must be an array of likely test file paths.\n"
            "file_edits must be an array of objects with keys: path, search, replace.\n"
            "Only propose small exact search/replace edits inside the listed target files.\n"
            "Never include more than 4 edits total.\n\n"
            f"Proposal:\n{json.dumps(proposal, ensure_ascii=True, indent=2)}\n\n"
            f"Target excerpts:\n{chr(10).join(excerpts)}"
        )
        raw = self.generate_text(model, prompt)
        parsed = json.loads(raw)
        if not isinstance(parsed, dict):
            raise ValueError("Autopilot plan was not a JSON object")
        edits = []
        for item in parsed.get("file_edits", []):
            if not isinstance(item, dict):
                continue
            edits.append(
                {
                    "path": str(item.get("path", "")).strip(),
                    "search": str(item.get("search", "")),
                    "replace": str(item.get("replace", "")),
                }
            )
        return {
            "summary": str(parsed.get("summary", "")).strip()[:280],
            "rationale": str(parsed.get("rationale", "")).strip()[:500],
            "selected_tests": [str(item).strip() for item in parsed.get("selected_tests", []) if str(item).strip()][:4],
            "file_edits": edits[:4],
        }

    def _apply_plan(self, worktree: Path, proposal: dict[str, Any], plan: dict[str, Any]) -> dict[str, Any]:
        allowed = {str(item).strip() for item in proposal.get("target_files", []) if str(item).strip()}
        changed_files: list[str] = []
        applied_edits: list[dict[str, Any]] = []
        for edit in plan.get("file_edits", []):
            path_text = str(edit.get("path", "")).strip()
            if path_text not in allowed:
                continue
            target = (worktree / path_text).resolve()
            if not str(target).startswith(str(worktree.resolve())) or not target.exists():
                continue
            before = target.read_text(encoding="utf-8", errors="ignore")
            search = str(edit.get("search", ""))
            replace = str(edit.get("replace", ""))
            if not search or search not in before:
                continue
            after = before.replace(search, replace, 1)
            if after == before:
                continue
            target.write_text(after, encoding="utf-8")
            changed_files.append(path_text)
            applied_edits.append({"path": path_text, "search_preview": search[:120], "replace_preview": replace[:120]})
        return {
            "changed_files": sorted(set(changed_files)),
            "applied_edits": applied_edits,
        }

    def _changed_paths(self, worktree: Path) -> list[str]:
        result = self._git(worktree, "diff", "--name-only")
        if result.returncode != 0:
            return []
        return [line.strip() for line in result.stdout.splitlines() if line.strip()]

    def _diff_numstat(self, worktree: Path) -> dict[str, Any]:
        result = self._git(worktree, "diff", "--numstat")
        if result.returncode != 0:
            return {"total_lines": 0, "files": []}
        total = 0
        files: list[dict[str, Any]] = []
        for line in result.stdout.splitlines():
            parts = line.split("\t")
            if len(parts) != 3:
                continue
            try:
                added = int(parts[0]) if parts[0].isdigit() else 0
                deleted = int(parts[1]) if parts[1].isdigit() else 0
            except ValueError:
                added = 0
                deleted = 0
            total += added + deleted
            files.append({"path": parts[2].strip(), "added": added, "deleted": deleted})
        return {"total_lines": total, "files": files}

    def _guess_selected_tests(self, proposal: dict[str, Any], plan: dict[str, Any], repo_root: Path) -> list[str]:
        selected = [str(item).strip() for item in plan.get("selected_tests", []) if str(item).strip()]
        normalized: list[str] = []
        for item in selected:
            candidate = (repo_root / item).resolve()
            if candidate.exists():
                normalized.append(candidate.relative_to(repo_root).as_posix())
        if normalized:
            return normalized[:4]

        guessed: list[str] = []
        for path_text in proposal.get("target_files", []):
            stem = Path(str(path_text)).stem.lower()
            test_path = repo_root / "tests" / f"test_{stem}.py"
            if test_path.exists():
                guessed.append(test_path.relative_to(repo_root).as_posix())
        return guessed[:4]

    def _run_tests(self, worktree: Path, selected_tests: list[str]) -> dict[str, Any]:
        if selected_tests:
            modules = [path.replace("/", ".").removesuffix(".py") for path in selected_tests]
            command = ["py", "-m", "unittest", *modules]
        else:
            command = ["py", "-m", "unittest", "discover", "-s", "tests", "-p", "test_*.py"]
        completed = subprocess.run(
            command,
            cwd=worktree,
            capture_output=True,
            text=True,
            timeout=300,
        )
        return {
            "command": " ".join(command),
            "selected_tests": selected_tests,
            "success": completed.returncode == 0,
            "return_code": completed.returncode,
            "stdout_tail": (completed.stdout or "").strip()[-3000:],
            "stderr_tail": (completed.stderr or "").strip()[-2000:],
        }

    def _safety_report(
        self,
        proposal: dict[str, Any],
        selected_tests: list[str],
        changed_paths: list[str],
        diff_stat: dict[str, Any],
        settings: dict[str, Any],
    ) -> dict[str, Any]:
        scoped_targets = {str(item).strip() for item in proposal.get("target_files", []) if str(item).strip()}
        scope_ok = bool(changed_paths) and all(path in scoped_targets for path in changed_paths)
        risky_paths = [path for path in changed_paths if path.startswith(self.RISKY_PREFIXES)]
        relevant_tests = bool(selected_tests) and any(Path(test).stem.replace("test_", "") in " ".join(changed_paths).lower() for test in selected_tests)
        large_diff = int(diff_stat.get("total_lines", 0)) > int(settings.get("max_changed_lines", 240) or 240)
        too_many_files = len(changed_paths) > int(settings.get("max_changed_files", 3) or 3)
        approved = scope_ok and relevant_tests and not large_diff and not too_many_files and not risky_paths
        reasons: list[str] = []
        if not scope_ok:
            reasons.append("Changed files drifted outside the proposal scope.")
        if not relevant_tests:
            reasons.append("No clearly relevant tests were selected for the touched files.")
        if large_diff:
            reasons.append("Diff size exceeded the autopilot safety limit.")
        if too_many_files:
            reasons.append("Too many files changed for a narrow autopilot pass.")
        if risky_paths:
            reasons.append("Risky paths were touched and require manual approval.")
        return {
            "approved": approved,
            "scope_ok": scope_ok,
            "relevant_tests_ok": relevant_tests,
            "large_diff_blocked": large_diff,
            "too_many_files_blocked": too_many_files,
            "risky_paths": risky_paths,
            "reasons": reasons,
            "changed_paths": changed_paths,
            "total_changed_lines": int(diff_stat.get("total_lines", 0)),
        }

    def _write_artifact(self, proposal: dict[str, Any], plan: dict[str, Any], execution: dict[str, Any], safety: dict[str, Any]) -> str:
        relative_path = f"autopilot/{str(proposal.get('id', 'proposal')).replace(':', '_').replace('/', '_')}.json"
        save_generated_file(
            relative_path,
            json.dumps(
                {
                    "proposal": proposal,
                    "plan": plan,
                    "execution": execution,
                    "safety": safety,
                },
                indent=2,
                ensure_ascii=True,
            ),
        )
        return f"generated/{relative_path}"

    def run_once(self, manual: bool = False) -> dict[str, Any]:
        with self._lock:
            snapshot = load_autopilot_snapshot()
            settings = dict(snapshot.get("settings", {}))
            proposal = self._active_proposal()
            if not manual and not settings.get("enabled", True):
                snapshot["state"] = "disabled"
                return save_autopilot_snapshot(snapshot)
            if proposal is None:
                snapshot["state"] = "idle"
                snapshot["active_proposal"] = None
                return save_autopilot_snapshot(snapshot)

            signature = self._proposal_signature(proposal)
            if not manual and signature == snapshot.get("last_signature", ""):
                snapshot["state"] = "idle"
                snapshot["active_proposal"] = proposal
                return save_autopilot_snapshot(snapshot)

            repo_root = self.indexer.project_roots[0].parent.parent if self.indexer.project_roots else Path.cwd()
            model = str(settings.get("model") or "qwen2.5:7b-instruct-q4_K_M")
            worktree: Path | None = None
            try:
                snapshot["state"] = "planning"
                snapshot["active_proposal"] = proposal
                save_autopilot_snapshot(snapshot)

                plan = self._build_plan(model, proposal)
                worktree = self._prepare_worktree(repo_root, str(proposal.get("id", "proposal")))
                execution = self._apply_plan(worktree, proposal, plan)
                changed_paths = self._changed_paths(worktree)
                diff_stat = self._diff_numstat(worktree)
                selected_tests = self._guess_selected_tests(proposal, plan, repo_root)
                tests = self._run_tests(worktree, selected_tests)
                safety = self._safety_report(proposal, selected_tests, changed_paths, diff_stat, settings)
                artifact_path = self._write_artifact(
                    proposal,
                    plan,
                    {
                        **execution,
                        "tests": tests,
                        "diff": diff_stat,
                    },
                    safety,
                )
                now = datetime.now(UTC).isoformat()
                snapshot.update(
                    {
                        "state": "ready",
                        "last_run_at": now,
                        "last_signature": signature,
                        "last_error": None,
                        "active_proposal": proposal,
                        "plan": plan,
                        "execution": {
                            **execution,
                            "tests": tests,
                            "diff": diff_stat,
                            "artifact_path": artifact_path,
                        },
                        "safety": safety,
                    }
                )
                save_autopilot_snapshot(snapshot)
                append_json_row(
                    KNOWLEDGE_FILE,
                    {
                        "timestamp": now,
                        "session_id": "background-autopilot",
                        "kind": "autopilot_execution",
                        "summary": f"Autopilot {'passed' if safety['approved'] else 'blocked'} proposal {proposal.get('title', 'proposal')} with {len(changed_paths)} changed file(s).",
                    },
                )
                return snapshot
            except Exception as exc:
                snapshot["state"] = "error"
                snapshot["last_error"] = str(exc)
                save_autopilot_snapshot(snapshot)
                return snapshot
            finally:
                if worktree is not None:
                    self._cleanup_worktree(repo_root, worktree)

    def _run_loop(self) -> None:
        while not self._stop_event.is_set():
            with self._lock:
                snapshot = load_autopilot_snapshot()
                poll_seconds = int(snapshot.get("settings", {}).get("poll_seconds", self.poll_seconds) or self.poll_seconds)
            try:
                self.run_once(manual=False)
            except Exception:
                pass
            self._stop_event.wait(poll_seconds)
