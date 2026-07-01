from __future__ import annotations

import tempfile
import unittest
from types import SimpleNamespace
from pathlib import Path
from unittest.mock import patch

from app.background_workspace_learning import BackgroundWorkspaceLearning


class BackgroundWorkspaceLearningTests(unittest.TestCase):
    def test_fallback_learning_creates_recent_items(self) -> None:
        learner = BackgroundWorkspaceLearning(generate_text=lambda *_: "{}")
        with tempfile.TemporaryDirectory(dir="D:/SWG-LLM") as tmp:
            uploads = Path(tmp) / "uploads"
            generated = Path(tmp) / "generated"
            uploads.mkdir(parents=True, exist_ok=True)
            generated.mkdir(parents=True, exist_ok=True)
            sample = uploads / "note.txt"
            sample.write_text("Important finding\nPotential risk", encoding="utf-8")

            with patch("app.background_workspace_learning.UPLOADS_DIR", uploads):
                with patch("app.background_workspace_learning.GENERATED_DIR", generated):
                    with patch("app.background_workspace_learning.save_workspace_learning_snapshot", side_effect=lambda snapshot: snapshot):
                        with patch("app.background_workspace_learning.append_json_row"):
                            with patch("app.background_workspace_learning.merge_proposals"):
                                with patch("app.background_workspace_learning.save_generated_file"):
                                    result = learner._run_once()

        self.assertEqual(result["state"], "ready")
        self.assertTrue(result["recent_items"])
        self.assertEqual(result["recent_items"][0]["source_path"], "uploads/note.txt")

    def test_repo_java_files_are_learned_from_project_roots(self) -> None:
        with tempfile.TemporaryDirectory(dir="D:/SWG-LLM") as tmp:
            repo_root = Path(tmp) / "swg-main" / "src"
            repo_root.mkdir(parents=True, exist_ok=True)
            java_file = repo_root / "Example.java"
            java_file.write_text(
                "public class Example {\n"
                "  public void improve() {\n"
                "    System.out.println(\"hello\");\n"
                "  }\n"
                "}\n",
                encoding="utf-8",
            )

            learner = BackgroundWorkspaceLearning(
                generate_text=lambda *_: "{}",
                indexer=SimpleNamespace(project_roots=[repo_root]),
            )
            with patch("app.background_workspace_learning.save_workspace_learning_snapshot", side_effect=lambda snapshot: snapshot):
                with patch("app.background_workspace_learning.append_json_row"):
                    with patch("app.background_workspace_learning.merge_proposals"):
                        with patch("app.background_workspace_learning.save_generated_file"):
                            result = learner._run_once()

        self.assertEqual(result["state"], "ready")
        self.assertTrue(result["recent_items"])
        self.assertEqual(result["recent_items"][0]["source_path"], "repo/src/Example.java")
        self.assertIn("proposal", result["recent_items"][0])


if __name__ == "__main__":
    unittest.main()
