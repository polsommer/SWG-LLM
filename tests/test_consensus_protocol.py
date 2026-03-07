from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from consensus.conflict_detector import ConflictDetector
from consensus.debate_engine import DebateEngine
from consensus.refinement_loop import RefinementConfig, RefinementLoop


def _mock_retriever(question: str, top_k: int) -> list[dict[str, object]]:
    return [
        {
            "chunk_id": f"chunk-{idx}",
            "score": 1.0 - idx * 0.1,
            "file_path": "docs/spec.md",
            "start_line": 10 + idx,
            "end_line": 11 + idx,
            "text": f"Evidence {idx} for {question}",
        }
        for idx in range(top_k)
    ]


class ConflictDetectorTests(unittest.TestCase):
    def test_detects_negation_conflict(self) -> None:
        detector = ConflictDetector()
        report = detector.detect(
            "The service is enabled in production.",
            "The service is not enabled in production.",
        )
        self.assertGreater(report.disagreement_score, 0.4)
        self.assertGreaterEqual(len(report.contradiction_pairs), 1)


class RefinementLoopTests(unittest.TestCase):
    def test_refinement_persists_merged_conclusion(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            tmp_path = Path(tmpdir)
            engine = DebateEngine(_mock_retriever, _mock_retriever, workdir=tmp_path)
            loop = RefinementLoop(
                debate_engine=engine,
                config=RefinementConfig(disagreement_threshold=0.2, agreement_threshold=0.8, max_rounds=2),
            )

            result = loop.run_and_persist("What changed?", top_k=2, filename="result.json")

            payload = json.loads((tmp_path / "result.json").read_text(encoding="utf-8"))
            self.assertIn("confidence", payload)
            self.assertIn("provenance", payload)
            self.assertIn("192.168.88.5", payload["provenance"])
            self.assertIn("192.168.88.10", payload["provenance"])
            self.assertGreaterEqual(result.conclusion.agreement_score, 0.0)


if __name__ == "__main__":
    unittest.main()
