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

    def test_project_roots_can_be_reconfigured_inside_workspace(self) -> None:
        with tempfile.TemporaryDirectory(dir="D:/SWG-LLM") as tmp:
            workspace_temp = Path(tmp)
            root_a = workspace_temp / "alpha"
            root_b = workspace_temp / "beta"
            root_a.mkdir(parents=True, exist_ok=True)
            root_b.mkdir(parents=True, exist_ok=True)

            indexer = ProjectIndexer()
            with patch("app.indexer.save_project_settings", side_effect=lambda settings: settings):
                result = indexer.configure_project_roots([str(root_a), str(root_b), str(root_a)])

            self.assertEqual(result["project_root_count"], 2)
            self.assertEqual(indexer.project_roots, [root_a.resolve(), root_b.resolve()])

    def test_micro_test_workflow_returns_conclusions_payload(self) -> None:
        agent = LocalAgent()
        fake_loop_result = {
            "reply": "1. What I tested\nA tiny repo check.\n2. What I observed\nIt found one likely path.\n3. Conclusion\nThe primary path appears stable.\n4. Follow-up\nInspect one more file if needed.",
            "created_files": [],
            "tool_events": ["Tool call: search_project {\"query\": \"login handler\"}"],
            "requires_approval": False,
            "approval_request": None,
        }
        with patch.object(agent, "_build_micro_test_prompt", return_value=("prompt", [])):
            with patch.object(agent, "_run_tool_loop", return_value=fake_loop_result):
                result = agent.run_micro_test("check login path", "demo", "session-1")

        self.assertIn("Conclusion", result["reply"])
        self.assertIn("updates", result)
        self.assertIn("figured_out", result)
        self.assertIn("ideas", result)
        self.assertFalse(result["requires_approval"])


if __name__ == "__main__":
    unittest.main()
