from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from ingestion.chunk_indexer import ChunkIndexer
from ingestion.knowledge_store import KnowledgeStore


class RagPipelineTests(unittest.TestCase):
    def test_chunk_metadata_is_attached(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            doc = root / "docs" / "guide.md"
            doc.parent.mkdir(parents=True, exist_ok=True)
            doc.write_text("# Operations Guide\n\n## Auth\nToken is required.", encoding="utf-8")

            chunks = list(ChunkIndexer(root, chunk_lines=10, overlap_lines=0).iter_chunks())
            self.assertEqual(len(chunks), 1)
            chunk = chunks[0]
            self.assertEqual(chunk.document_title, "Operations Guide")
            self.assertEqual(chunk.section, "Auth")
            self.assertEqual(chunk.source_kind, "internal_docs")
            self.assertEqual(chunk.access_scope, "public")

    def test_hybrid_retrieval_returns_ranked_results_and_logs(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            (root / "docs").mkdir(parents=True, exist_ok=True)
            (root / "docs" / "alpha.md").write_text(
                "# Auth\nJWT token validation and refresh token flow.", encoding="utf-8"
            )
            (root / "code.py").write_text(
                "def run_worker():\n    return 'cluster heartbeat scheduler'", encoding="utf-8"
            )

            store = KnowledgeStore(store_dir=root / ".swg_knowledge")
            summary = store.index_repository(
                repo_root=root,
                revision="rev-1",
                indexer=ChunkIndexer(root, chunk_lines=20, overlap_lines=0),
            )
            self.assertEqual(summary["status"], "updated")

            results = store.query("How does auth token refresh work?", top_k=2)
            self.assertEqual(len(results), 2)
            self.assertGreaterEqual(results[0].rerank_score, results[1].rerank_score)
            self.assertTrue((root / ".swg_knowledge" / "retrieval_metrics.jsonl").exists())

            events = [
                json.loads(line)
                for line in (root / ".swg_knowledge" / "retrieval_metrics.jsonl").read_text(encoding="utf-8").splitlines()
            ]
            self.assertTrue(any(event["hit"] for event in events))


if __name__ == "__main__":
    unittest.main()
