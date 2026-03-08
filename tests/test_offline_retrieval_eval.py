from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from ingestion.chunk_indexer import ChunkIndexer
from ingestion.knowledge_store import KnowledgeStore


class OfflineRetrievalEvalTests(unittest.TestCase):
    def test_retrieval_required_cases_improve_hit_rate(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            (root / "docs").mkdir(parents=True, exist_ok=True)
            (root / "docs" / "runbook.md").write_text(
                "# Incident Runbook\n\n## cache flush\nUse command swgctl cache flush --all\n\n"
                "## leader check\nUse command swgctl cluster leader --verbose",
                encoding="utf-8",
            )
            (root / "docs" / "faq.md").write_text(
                "# FAQ\n\n## retention\nLogs are retained for 14 days in standard tier.",
                encoding="utf-8",
            )

            store = KnowledgeStore(store_dir=root / ".store")
            store.index_repository(root, "rev", ChunkIndexer(root, chunk_lines=15, overlap_lines=0))

            eval_cases = [
                ("What command flushes all cache entries?", "swgctl cache flush --all"),
                ("How do we inspect the current leader?", "swgctl cluster leader --verbose"),
                ("How long are logs retained?", "14 days"),
            ]

            baseline_hits = 0
            rag_hits = 0
            for question, expected in eval_cases:
                baseline_answer = "I don't know"
                if expected.lower() in baseline_answer.lower():
                    baseline_hits += 1

                top = store.query(question, top_k=1)
                rag_answer = top[0].text if top else ""
                if expected.lower() in rag_answer.lower():
                    rag_hits += 1

            self.assertEqual(baseline_hits, 0)
            self.assertGreaterEqual(rag_hits, 2)


if __name__ == "__main__":
    unittest.main()
