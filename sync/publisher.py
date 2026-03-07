"""Publisher pipeline for exporting clustered debate artifacts to a Git repository."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import logging
import os
from pathlib import Path
import subprocess
import time
from typing import Iterable
from urllib.parse import urlparse, urlunparse

DEFAULT_TARGET_REPO = "https://github.com/polsommer/llm-dsrc.git"
DEFAULT_BRANCH = "swg/publisher"
DEFAULT_COMMIT_MESSAGE = "feat: clustered debate findings from dsrc ingestion"
DEFAULT_WORKSPACE = ".swg"
DEFAULT_ARTIFACT_NAME = "clustered-debate-findings.md"


@dataclass(frozen=True)
class PublisherPayload:
    """Input payload used to construct a markdown artifact."""

    findings: Iterable[str]
    ideas: Iterable[str]
    rationale: Iterable[str]
    source_links: Iterable[str]
    title: str = "Clustered Debate Findings"


@dataclass(frozen=True)
class PublishResult:
    """Represents final publish metadata."""

    repo_path: Path
    artifact_path: Path
    branch: str
    commit_sha: str


class PublisherPipeline:
    """Build markdown artifacts and publish them into a target git repository."""

    def __init__(
        self,
        token: str | None = None,
        repo_url: str = DEFAULT_TARGET_REPO,
        branch: str = DEFAULT_BRANCH,
        commit_message: str = DEFAULT_COMMIT_MESSAGE,
        workspace: Path | None = None,
        retries: int = 3,
        backoff_seconds: float = 1.0,
        logger: logging.Logger | None = None,
    ) -> None:
        self.token = token or os.getenv("GITHUB_TOKEN")
        self.repo_url = repo_url
        self.branch = branch
        self.commit_message = commit_message
        self.retries = retries
        self.backoff_seconds = backoff_seconds
        self.workspace = (workspace or Path(os.getenv("SWG_WORKDIR", DEFAULT_WORKSPACE))).expanduser().resolve()
        self.repo_path = self.workspace / "target" / "llm-dsrc"
        self.logger = logger or logging.getLogger(__name__)

    def publish(self, payload: PublisherPayload) -> PublishResult:
        """Execute full publication workflow and return commit metadata."""
        self._sync_repo()
        self._checkout_branch()

        artifact_path = self._write_artifact(payload)

        self._run_git(["add", str(artifact_path.relative_to(self.repo_path))])
        if not self._has_staged_changes():
            sha = self._current_sha()
            self.logger.info("No changes to commit; current SHA: %s", sha)
            return PublishResult(self.repo_path, artifact_path, self.branch, sha)

        self._run_git(["commit", "-m", self.commit_message])
        self._run_git(["push", "-u", "origin", self.branch])

        sha = self._current_sha()
        self.logger.info("Published artifacts commit SHA: %s", sha)
        return PublishResult(self.repo_path, artifact_path, self.branch, sha)

    def _sync_repo(self) -> None:
        """Clone repository if absent, otherwise pull latest refs."""
        self.repo_path.parent.mkdir(parents=True, exist_ok=True)
        if not (self.repo_path / ".git").exists():
            self._run(
                ["git", "clone", self._repo_url_with_token(), str(self.repo_path)],
                redact_tokens=True,
            )
            return

        self._run_git(["remote", "set-url", "origin", self._repo_url_with_token()], redact_tokens=True)
        self._run_git(["fetch", "--all", "--prune"])
        self._run_git(["pull", "--ff-only", "origin", "main"])

    def _checkout_branch(self) -> None:
        """Ensure local checkout is on target branch."""
        self._run_git(["checkout", "-B", self.branch])

    def _write_artifact(self, payload: PublisherPayload) -> Path:
        """Write markdown artifact under reports/YYYY-MM-DD/."""
        report_date = datetime.now(timezone.utc).date().isoformat()
        report_dir = self.repo_path / "reports" / report_date
        report_dir.mkdir(parents=True, exist_ok=True)

        artifact_path = report_dir / DEFAULT_ARTIFACT_NAME
        artifact_path.write_text(self._render_markdown(payload), encoding="utf-8")
        return artifact_path

    @staticmethod
    def _render_markdown(payload: PublisherPayload) -> str:
        """Render findings payload into markdown document."""
        sections = [
            f"# {payload.title}",
            "",
            PublisherPipeline._render_bullets("Findings", payload.findings),
            "",
            PublisherPipeline._render_bullets("Ideas", payload.ideas),
            "",
            PublisherPipeline._render_bullets("Rationale", payload.rationale),
            "",
            PublisherPipeline._render_bullets("Source Links", payload.source_links),
            "",
        ]
        return "\n".join(sections)

    @staticmethod
    def _render_bullets(header: str, values: Iterable[str]) -> str:
        items = list(values)
        lines = [f"## {header}"]
        if not items:
            lines.append("- _None provided._")
            return "\n".join(lines)

        lines.extend(f"- {item}" for item in items)
        return "\n".join(lines)

    def _repo_url_with_token(self) -> str:
        """Embed token into HTTPS URL when provided for non-interactive auth."""
        if not self.token:
            return self.repo_url

        parsed = urlparse(self.repo_url)
        if parsed.scheme not in {"http", "https"}:
            return self.repo_url

        if "@" in parsed.netloc:
            return self.repo_url

        netloc = f"x-access-token:{self.token}@{parsed.netloc}"
        return urlunparse(parsed._replace(netloc=netloc))

    def _run_git(self, args: list[str], redact_tokens: bool = False) -> str:
        return self._run(["git", "-C", str(self.repo_path), *args], redact_tokens=redact_tokens)

    def _run(self, cmd: list[str], redact_tokens: bool = False) -> str:
        """Run a shell command with retry/backoff."""
        attempt = 0
        while True:
            try:
                result = subprocess.run(cmd, check=True, text=True, capture_output=True)
                return result.stdout.strip()
            except (OSError, subprocess.CalledProcessError) as exc:
                attempt += 1
                if attempt > self.retries:
                    raise

                shown_cmd = cmd
                if redact_tokens and self.token:
                    shown_cmd = [part.replace(self.token, "***") for part in cmd]
                wait_time = self.backoff_seconds * (2 ** (attempt - 1))
                self.logger.warning(
                    "Command failed (attempt %s/%s): %s; retrying in %.2fs",
                    attempt,
                    self.retries,
                    " ".join(shown_cmd),
                    wait_time,
                )
                self.logger.debug("Command stderr: %s", getattr(exc, "stderr", ""))
                time.sleep(wait_time)

    def _has_staged_changes(self) -> bool:
        result = subprocess.run(
            ["git", "-C", str(self.repo_path), "diff", "--cached", "--quiet"],
            text=True,
            capture_output=True,
            check=False,
        )
        if result.returncode == 0:
            return False
        if result.returncode == 1:
            return True
        raise subprocess.CalledProcessError(
            returncode=result.returncode,
            cmd=result.args,
            output=result.stdout,
            stderr=result.stderr,
        )

    def _current_sha(self) -> str:
        return self._run_git(["rev-parse", "HEAD"])


__all__ = [
    "DEFAULT_BRANCH",
    "DEFAULT_COMMIT_MESSAGE",
    "DEFAULT_TARGET_REPO",
    "PublishResult",
    "PublisherPayload",
    "PublisherPipeline",
]
