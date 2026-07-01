from __future__ import annotations

import unittest

from app.background_council import BackgroundCouncil


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
        )
        self.assertFalse(failed["approved"])


if __name__ == "__main__":
    unittest.main()
