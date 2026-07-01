from __future__ import annotations

import unittest

from app.git_publisher import GitPublisher


class GitPublisherTests(unittest.TestCase):
    def test_expected_origin_matching_accepts_github_variants(self) -> None:
        publisher = GitPublisher()
        self.assertTrue(publisher._origin_matches_expected("https://github.com/polsommer/SWG-LLM.git"))
        self.assertTrue(publisher._origin_matches_expected("git@github.com:polsommer/SWG-LLM.git"))
        self.assertFalse(publisher._origin_matches_expected("https://github.com/other/repo.git"))


if __name__ == "__main__":
    unittest.main()
