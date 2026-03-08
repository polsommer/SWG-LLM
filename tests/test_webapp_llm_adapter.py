from __future__ import annotations

import unittest
from unittest.mock import patch

from ingestion.knowledge_store import QueryResult
from webapp.llm_adapter import build_prompt, generate_answer, get_backend_name


class LLMAdapterTests(unittest.TestCase):
    def _doc(self) -> QueryResult:
        return QueryResult(
            chunk_id="c1",
            score=1.0,
            file_path="README.md",
            start_line=10,
            end_line=12,
            text="Important project details.",
            semantic_score=1.0,
            keyword_score=0.5,
            rerank_score=0.9,
            document_title="doc",
            section="root",
            last_updated="now",
            access_scope="restricted",
            source_kind="code",
        )

    def test_build_prompt_includes_evidence(self) -> None:
        prompt = build_prompt("What is this?", [self._doc()])
        self.assertIn("README.md:10-12", prompt)
        self.assertIn("Important project details.", prompt)

    def test_mock_backend_abstains_on_empty_evidence(self) -> None:
        prompt = build_prompt("Unknown", [])
        answer = generate_answer(prompt)
        self.assertIn("enough evidence", answer.lower())

    def test_get_backend_defaults_to_mock(self) -> None:
        with patch.dict("os.environ", {}, clear=True):
            self.assertEqual(get_backend_name(), "mock")


if __name__ == "__main__":
    unittest.main()
