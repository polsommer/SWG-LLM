from __future__ import annotations

import tempfile
import unittest
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
                            with patch("app.background_workspace_learning.save_generated_file"):
                                result = learner._run_once()

        self.assertEqual(result["state"], "ready")
        self.assertTrue(result["recent_items"])
        self.assertEqual(result["recent_items"][0]["source_path"], "uploads/note.txt")


if __name__ == "__main__":
    unittest.main()
