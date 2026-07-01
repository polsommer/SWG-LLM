from __future__ import annotations

import unittest

from app.background_intelligence import BackgroundIntelligence


class FakeIndexer:
    def get_status(self) -> dict:
        return {
            "indexed_at": "2026-07-01T12:00:00+00:00",
            "file_count": 120,
            "chunk_count": 480,
            "truncated_file_count": 3,
            "index_mode": "deep",
            "summary": {
                "top_symbols": [{"name": "CreatureObject", "count": 12}],
                "top_imports": [{"name": "server/game/CreatureObject.h", "count": 8}],
                "top_functions": [{"name": "handleLogin", "count": 5}],
                "top_connected_symbols": [{"name": "CreatureObject", "count": 14}],
                "semantic_top_terms": [{"name": "combat", "count": 10}],
                "largest_files": [{"path": "swg-main/src/server/game/CreatureObject.cpp", "size": 1000, "chunk_count": 5}],
                "graph_edge_count": 32,
                "top_extensions": [{"name": ".cpp", "count": 70}],
            },
        }


class BackgroundIntelligenceTests(unittest.TestCase):
    def test_run_analysis_builds_workboard_snapshot(self) -> None:
        intelligence = BackgroundIntelligence(indexer=FakeIndexer(), poll_seconds=999)
        snapshot = intelligence._run_analysis()

        self.assertEqual(snapshot["status"], "ready")
        self.assertTrue(snapshot["briefing"])
        self.assertTrue(snapshot["focus_areas"])
        self.assertTrue(snapshot["suggested_tasks"])
        self.assertTrue(snapshot["repo_hypotheses"])
        self.assertTrue(snapshot["signals"])


if __name__ == "__main__":
    unittest.main()
