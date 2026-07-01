from __future__ import annotations

import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

from app.background_autopilot import BackgroundAutopilot


class FakeIndexer:
    def __init__(self, repo_root: Path) -> None:
        self.project_roots = [repo_root / "swg-main" / "src"]

    def _resolve_project_path(self, relative_path: str) -> Path:
        return (self.project_roots[0].parent / relative_path).resolve()


class BackgroundAutopilotTests(unittest.TestCase):
    def test_safety_report_blocks_out_of_scope_or_irrelevant_tests(self) -> None:
        autopilot = BackgroundAutopilot(generate_text=lambda *_: "{}", indexer=FakeIndexer(Path("D:/SWG-LLM")))
        report = autopilot._safety_report(
            proposal={"target_files": ["app/background_council.py"]},
            selected_tests=["tests/test_background_intelligence.py"],
            changed_paths=["app/background_council.py", "app/main.py"],
            diff_stat={"total_lines": 40},
            settings={"max_changed_lines": 240, "max_changed_files": 3},
        )
        self.assertFalse(report["approved"])
        self.assertFalse(report["scope_ok"])

    def test_run_once_records_execution_artifact(self) -> None:
        with tempfile.TemporaryDirectory(dir="D:/SWG-LLM") as tmp:
            repo_root = Path(tmp)
            (repo_root / "swg-main" / "src").mkdir(parents=True, exist_ok=True)
            target = repo_root / "swg-main" / "src" / "demo.py"
            target.write_text("VALUE = 1\n", encoding="utf-8")

            autopilot = BackgroundAutopilot(generate_text=lambda *_: "{}", indexer=FakeIndexer(repo_root))
            proposal = {
                "id": "intel:demo",
                "title": "Improve demo",
                "target_files": ["src/demo.py"],
                "suspected_problem": "Needs a small tweak.",
                "suggested_change": "Adjust the value safely.",
                "expected_test_impact": "A focused test should validate the new value.",
            }
            with patch("app.background_autopilot.load_proposals_snapshot", return_value={"active_proposal": proposal, "recent_proposals": [proposal]}):
                with patch("app.background_autopilot.load_autopilot_snapshot", return_value={"settings": {"enabled": True, "model": "qwen2.5:7b-instruct-q4_K_M", "max_changed_lines": 240, "max_changed_files": 3}, "last_signature": "", "plan": {}, "execution": {}, "safety": {}}):
                    with patch("app.background_autopilot.save_autopilot_snapshot", side_effect=lambda snapshot: snapshot):
                        with patch("app.background_autopilot.append_json_row"):
                            with patch.object(autopilot, "_build_plan", return_value={"summary": "Small update", "rationale": "Safe test patch", "selected_tests": ["tests/test_demo.py"], "file_edits": [{"path": "src/demo.py", "search": "VALUE = 1", "replace": "VALUE = 2"}]}):
                                with patch.object(autopilot, "_prepare_worktree", return_value=repo_root / "swg-main"):
                                    with patch.object(autopilot, "_cleanup_worktree"):
                                        with patch.object(autopilot, "_run_tests", return_value={"command": "py -m unittest tests.test_demo", "selected_tests": ["tests/test_demo.py"], "success": True, "return_code": 0, "stdout_tail": "", "stderr_tail": ""}):
                                            result = autopilot.run_once(manual=True)

        self.assertEqual(result["state"], "ready")
        self.assertEqual(result["execution"]["changed_files"], ["src/demo.py"])
        self.assertIn("artifact_path", result["execution"])

    def test_run_loop_does_not_hold_status_lock_while_running(self) -> None:
        autopilot = BackgroundAutopilot(generate_text=lambda *_: "{}", indexer=FakeIndexer(Path("D:/SWG-LLM")), poll_seconds=1)
        autopilot._stop_event = threading.Event()

        with patch("app.background_autopilot.load_autopilot_snapshot", return_value={"settings": {"poll_seconds": 1}}):
            with patch.object(autopilot, "run_once", side_effect=lambda manual=False: autopilot._stop_event.set() or {"state": "ready"}):
                worker = threading.Thread(target=autopilot._run_loop, daemon=True)
                worker.start()
                worker.join(timeout=2)

        self.assertFalse(worker.is_alive())


if __name__ == "__main__":
    unittest.main()
