from __future__ import annotations

import unittest
from unittest.mock import patch

from app.background_council import BackgroundCouncil


class FakeGitPublisher:
    def __init__(self, status: dict) -> None:
        self._status = status
        self.repo_root = "."

    def worktree_status(self) -> dict:
        return dict(self._status)

    def publish_worktree(self, commit_message: str, push_to_remote: bool) -> dict:
        return {
            "committed": True,
            "pushed": push_to_remote,
            "branch": self._status.get("branch", "main"),
            "message": commit_message,
        }


class BackgroundCouncilTests(unittest.TestCase):
    def test_decide_requires_passing_tests_and_threshold(self) -> None:
        council = BackgroundCouncil(generate_text=lambda *_: "{}")
        decision = council._decide(
            votes=[
                {"vote": "approve", "commit_message": "Ship it"},
                {"vote": "approve", "commit_message": "Ship it"},
                {"vote": "revise", "commit_message": "Revise"},
            ],
            test_result={"success": True},
            threshold=2,
            auto_commit_enabled=True,
            has_changes=True,
        )
        self.assertTrue(decision["approved"])
        self.assertEqual(decision["approve_votes"], 2)

        failed = council._decide(
            votes=[
                {"vote": "approve", "commit_message": "Ship it"},
                {"vote": "approve", "commit_message": "Ship it"},
                {"vote": "approve", "commit_message": "Ship it"},
            ],
            test_result={"success": False},
            threshold=2,
            auto_commit_enabled=True,
            has_changes=True,
        )
        self.assertFalse(failed["approved"])

    def test_fallback_vote_approves_only_when_tests_pass(self) -> None:
        council = BackgroundCouncil(generate_text=lambda *_: "{}")
        approved = council._fallback_vote(
            name="Reviewer",
            git_status={"has_changes": True, "branch": "main"},
            test_result={"success": True},
            reason="ollama unavailable",
        )
        self.assertEqual(approved["vote"], "approve")

        revise = council._fallback_vote(
            name="Reviewer",
            git_status={"has_changes": True, "branch": "main"},
            test_result={"success": False},
            reason="ollama unavailable",
        )
        self.assertEqual(revise["vote"], "revise")

    def test_decide_does_not_auto_approve_without_worktree_changes(self) -> None:
        council = BackgroundCouncil(generate_text=lambda *_: "{}")
        decision = council._decide(
            votes=[
                {"vote": "approve", "commit_message": "Ship it"},
                {"vote": "approve", "commit_message": "Ship it"},
                {"vote": "approve", "commit_message": "Ship it"},
            ],
            test_result={"success": True},
            threshold=2,
            auto_commit_enabled=True,
            has_changes=False,
        )
        self.assertFalse(decision["approved"])
        self.assertIn("no worktree changes", decision["rationale"].lower())

    def test_run_once_uses_learning_inputs_even_without_git_changes(self) -> None:
        model_output = '{"vote":"approve","confidence":0.8,"rationale":"Use the learned repo hotspot next.","commit_message":"Follow learning guidance"}'
        council = BackgroundCouncil(
            generate_text=lambda *_: model_output,
            git_publisher=FakeGitPublisher(
                {
                    "has_changes": False,
                    "branch": "main",
                    "entries": [],
                    "diff_stat": "",
                    "origin_matches_expected": True,
                }
            ),
        )
        with patch.object(council, "_run_tests", return_value={"success": True, "command": "py -m unittest", "stdout_tail": "", "stderr_tail": ""}):
            with patch("app.background_council.load_council_snapshot", return_value={"settings": {"enabled": True, "auto_commit_enabled": False, "auto_push_enabled": False, "poll_seconds": 45, "model": "qwen2.5:7b-instruct-q4_K_M", "auto_approve_threshold": 2, "test_command": "py -m unittest"}, "last_signature": "", "transcript": [], "votes": [], "decision": {}, "tests": {}, "git": {}}):
                with patch("app.background_council.save_council_snapshot", side_effect=lambda snapshot: snapshot):
                    with patch("app.background_council.append_json_row"):
                        with patch(
                            "app.background_council.load_intelligence_snapshot",
                            return_value={
                                "last_run_at": "2026-07-01T10:00:00+00:00",
                                "focus_areas": [{"title": "High-traffic symbol: CombatManager", "reason": "Touches several flows."}],
                                "suggested_tasks": [{"title": "Trace CombatManager", "reason": "Likely hotspot."}],
                            },
                        ):
                            with patch(
                                "app.background_council.load_workspace_learning_snapshot",
                                return_value={
                                    "last_run_at": "2026-07-01T10:01:00+00:00",
                                    "recent_items": [{"source_path": "repo/src/Example.java", "conclusion": "This file should be simplified and covered by a targeted test."}],
                                },
                            ):
                                result = council.run_once(manual=False)

        self.assertEqual(result["state"], "ready")
        self.assertTrue(result["transcript"])
        self.assertIn("context", result)
        self.assertFalse(result["decision"]["approved"])
        self.assertEqual(result["context"]["workspace_learning"]["recent_items"][0]["source_path"], "repo/src/Example.java")


if __name__ == "__main__":
    unittest.main()
