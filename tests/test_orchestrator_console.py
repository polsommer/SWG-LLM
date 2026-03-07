from __future__ import annotations

import io
import unittest
from pathlib import Path

from orchestrator.__main__ import _compute_metrics, run_runtime_console
from orchestrator.cluster_manager import ClusterManager


def _config() -> dict[str, object]:
    return {
        "cluster_control": {
            "coordinator_mode": "fixed",
            "fixed_coordinator_id": "agent_192_168_88_5",
            "heartbeat_ttl_seconds": 30,
        },
        "nodes": [
            {"address": "192.168.88.5", "role": "orchestrator_agent", "agent_id": "agent_192_168_88_5", "enabled": True},
            {"address": "192.168.88.10", "role": "reviewer_agent", "agent_id": "agent_192_168_88_10", "enabled": True},
        ],
    }


class OrchestratorConsoleTests(unittest.TestCase):
    def test_compute_metrics_defaults(self) -> None:
        manager = ClusterManager(_config())
        metrics = _compute_metrics(manager, started_monotonic=10.0, now_monotonic=20.0)
        self.assertEqual(metrics.total_tasks, 0)
        self.assertEqual(metrics.success_rate, 1.0)
        self.assertEqual(metrics.telemetry_events, 0)

    def test_runtime_console_generates_status_and_talk(self) -> None:
        manager = ClusterManager(_config())
        output = io.StringIO()
        clock = {"t": 0.0}

        def _sleep(seconds: float) -> None:
            clock["t"] += seconds

        def _monotonic() -> float:
            return clock["t"]

        run_runtime_console(
            manager=manager,
            config_path=Path("config/cluster.yaml"),
            refresh_seconds=0.1,
            max_ticks=2,
            stream=output,
            sleep_fn=_sleep,
            monotonic_fn=_monotonic,
        )

        text = output.getvalue()
        self.assertIn("SWG-LLM Live Console", text)
        self.assertIn("Node Status", text)
        self.assertIn("Conversation + Telemetry", text)
        self.assertIn("proposal emitted", text)
        self.assertIn("review completed", text)


if __name__ == "__main__":
    unittest.main()
