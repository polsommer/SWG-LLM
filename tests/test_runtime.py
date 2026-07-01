from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app.agent import LocalAgent
from app.indexer import ProjectIndexer
from app.storage import read_text_file


class FakeResponse:
    def __init__(self, status_code: int, payload: dict[str, object]) -> None:
        self.status_code = status_code
        self._payload = payload
        self.text = str(payload)

    def json(self) -> dict[str, object]:
        return self._payload


class RuntimeTests(unittest.TestCase):
    def test_read_text_file_respects_max_chars(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "sample.txt"
            path.write_text("abcdefghij", encoding="utf-8")
            self.assertEqual(read_text_file(path, max_chars=4), "abcd")

    def test_ollama_model_not_found_error_is_actionable(self) -> None:
        agent = LocalAgent()
        with patch("app.agent.requests.post", return_value=FakeResponse(404, {"error": "model 'demo' not found"})):
            with self.assertRaisesRegex(RuntimeError, "ollama pull demo"):
                agent._ollama_generate("demo", "hello")

    def test_large_repo_uses_fast_index_mode(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            temp_root = Path(tmp) / "src"
            package_dir = temp_root / "pkg"
            package_dir.mkdir(parents=True, exist_ok=True)
            for index in range(2):
                (package_dir / f"File{index}.java").write_text(
                    "class Demo {\n"
                    "  void alpha() {}\n"
                    "  void beta() {}\n"
                    "  void gamma() {}\n"
                    "}\n" * 40,
                    encoding="utf-8",
                )

            manifest = Path(tmp) / "manifest.json"
            chunks = Path(tmp) / "chunks.json"
            graph = Path(tmp) / "graph.json"
            semantic = Path(tmp) / "semantic.json"
            status = Path(tmp) / "status.json"

            with patch.multiple(
                "app.indexer",
                MANIFEST_FILE=manifest,
                CHUNKS_FILE=chunks,
                GRAPH_FILE=graph,
                SEMANTIC_FILE=semantic,
                STATUS_FILE=status,
                FAST_INDEX_FILE_THRESHOLD=1,
                FAST_INDEX_TEXT_CHARS_PER_FILE=80,
                FAST_INDEX_CHUNK_CHARS=40,
                FAST_INDEX_CHUNKS_PER_FILE=1,
            ):
                indexer = ProjectIndexer()
                indexer.project_roots = [temp_root]
                result = indexer.index_project()

            self.assertEqual(result["index_mode"], "fast")
            self.assertEqual(result["file_count"], 2)
            self.assertEqual(result["chunk_count"], 2)
            self.assertGreaterEqual(result["truncated_file_count"], 1)


if __name__ == "__main__":
    unittest.main()
