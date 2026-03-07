"""Repository ingestion orchestration utilities."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import os
import subprocess


DEFAULT_DSRC_REPO = "https://github.com/SWG-Source/dsrc.git"


@dataclass(frozen=True)
class RepositoryState:
    """Represents the checked-out repository state."""

    local_path: Path
    revision: str


class RepositoryIngestor:
    """Clone/pull source repository to a controlled local workspace."""

    def __init__(
        self,
        repo_url: str = DEFAULT_DSRC_REPO,
        workspace: Path | None = None,
        repo_dir_name: str = "dsrc",
    ) -> None:
        base = workspace or Path(os.getenv("SWG_WORKDIR", ".swg"))
        self.repo_url = repo_url
        self.workspace = base.expanduser().resolve()
        self.repo_path = self.workspace / "source" / repo_dir_name

    def sync(self) -> RepositoryState:
        """Clone missing repo or pull updates for existing repo."""
        self.repo_path.parent.mkdir(parents=True, exist_ok=True)

        if not (self.repo_path / ".git").exists():
            self._run(["git", "clone", self.repo_url, str(self.repo_path)])
        else:
            self._run(["git", "-C", str(self.repo_path), "fetch", "--all", "--prune"])
            self._run(["git", "-C", str(self.repo_path), "pull", "--ff-only"])

        revision = self._run(
            ["git", "-C", str(self.repo_path), "rev-parse", "HEAD"],
            capture_output=True,
        ).strip()
        return RepositoryState(local_path=self.repo_path, revision=revision)

    @staticmethod
    def _run(cmd: list[str], capture_output: bool = False) -> str:
        result = subprocess.run(
            cmd,
            check=True,
            text=True,
            capture_output=capture_output,
        )
        return result.stdout if capture_output else ""
