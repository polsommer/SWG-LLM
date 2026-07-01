from __future__ import annotations

import subprocess
from pathlib import Path
from typing import Any

from .storage import BASE_DIR


class GitPublisher:
    EXPECTED_REMOTE = "polsommer/SWG-LLM"

    def __init__(self, repo_root: Path | None = None) -> None:
        self.repo_root = (repo_root or BASE_DIR).resolve()

    def _git(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", "-c", f"safe.directory={self.repo_root}", *args],
            cwd=self.repo_root,
            capture_output=True,
            text=True,
            timeout=60,
        )

    def _ensure_relative_paths(self, paths: list[Path]) -> list[str]:
        relative_paths: list[str] = []
        for path in paths:
            resolved = path.resolve()
            if not str(resolved).startswith(str(self.repo_root)):
                raise ValueError(f"Refusing to publish file outside repo root: {resolved}")
            relative_paths.append(resolved.relative_to(self.repo_root).as_posix())
        if not relative_paths:
            raise ValueError("At least one file is required for git publishing")
        return relative_paths

    def _current_branch(self) -> str:
        result = self._git("branch", "--show-current")
        if result.returncode != 0:
            raise RuntimeError(result.stderr.strip() or "Could not determine current branch")
        return result.stdout.strip() or "HEAD"

    def _origin_url(self) -> str | None:
        result = self._git("remote", "get-url", "origin")
        if result.returncode != 0:
            return None
        return result.stdout.strip() or None

    def _origin_matches_expected(self, origin_url: str | None) -> bool:
        if not origin_url:
            return False
        normalized = origin_url.strip().lower().replace(".git", "")
        return normalized.endswith(self.EXPECTED_REMOTE.lower())

    def publish_files(
        self,
        *,
        paths: list[Path],
        commit_message: str,
        push_to_remote: bool = False,
    ) -> dict[str, Any]:
        relative_paths = self._ensure_relative_paths(paths)

        add_result = self._git("add", *relative_paths)
        if add_result.returncode != 0:
            raise RuntimeError(add_result.stderr.strip() or "git add failed")

        commit_result = self._git("commit", "-m", commit_message, "--", *relative_paths)
        if commit_result.returncode != 0:
            combined = (commit_result.stdout + "\n" + commit_result.stderr).strip()
            if "nothing to commit" in combined.lower():
                return {
                    "committed": False,
                    "pushed": False,
                    "branch": self._current_branch(),
                    "origin_url": self._origin_url(),
                    "message": "No file changes were detected for the selected consensus artifact.",
                    "paths": relative_paths,
                }
            raise RuntimeError(combined or "git commit failed")

        branch = self._current_branch()
        origin_url = self._origin_url()
        pushed = False
        push_output = ""

        if push_to_remote:
            if not self._origin_matches_expected(origin_url):
                raise RuntimeError(
                    f"Refusing to push because origin does not match the expected repo `{self.EXPECTED_REMOTE}`. "
                    f"Current origin: {origin_url or 'not configured'}"
                )
            push_result = self._git("push", "origin", branch)
            if push_result.returncode != 0:
                raise RuntimeError(push_result.stderr.strip() or push_result.stdout.strip() or "git push failed")
            pushed = True
            push_output = (push_result.stdout or push_result.stderr).strip()

        return {
            "committed": True,
            "pushed": pushed,
            "branch": branch,
            "origin_url": origin_url,
            "message": (commit_result.stdout or commit_result.stderr).strip(),
            "push_output": push_output,
            "paths": relative_paths,
            "expected_remote": f"https://github.com/{self.EXPECTED_REMOTE}",
        }
