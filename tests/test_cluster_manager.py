from __future__ import annotations

from datetime import datetime, timedelta, timezone
import unittest

from orchestrator.cluster_manager import ClusterManager


def _config(mode: str = "fixed") -> dict[str, object]:
    return {
        "cluster_control": {
            "coordinator_mode": mode,
            "fixed_coordinator_id": "agent_192_168_88_5",
            "heartbeat_ttl_seconds": 30,
        },
        "nodes": [
            {
                "address": "192.168.88.5",
                "role": "orchestrator_agent",
                "agent_id": "agent_192_168_88_5",
                "enabled": True,
                "health_endpoint": "http://192.168.88.5:8080/healthz",
            },
            {
                "address": "192.168.88.10",
                "role": "reviewer_agent",
                "agent_id": "agent_192_168_88_10",
                "enabled": True,
                "health_endpoint": "http://192.168.88.10:8080/healthz",
            },
        ],
    }


class Clock:
    def __init__(self, start: datetime) -> None:
        self.now = start

    def advance(self, seconds: int) -> None:
        self.now += timedelta(seconds=seconds)

    def __call__(self) -> datetime:
        return self.now


class ClusterManagerTests(unittest.TestCase):
    def test_heartbeat_health_and_stale_detection(self) -> None:
        clock = Clock(datetime(2024, 1, 1, tzinfo=timezone.utc))
        manager = ClusterManager(_config(), now_provider=clock)

        manager.heartbeat("agent_192_168_88_5", healthy=True)
        status = manager.health_status()
        self.assertTrue(status["agent_192_168_88_5"]["healthy"])
        self.assertFalse(status["agent_192_168_88_5"]["stale"])

        clock.advance(31)
        status = manager.health_status()
        self.assertFalse(status["agent_192_168_88_5"]["healthy"])
        self.assertTrue(status["agent_192_168_88_5"]["stale"])

    def test_coordinator_modes(self) -> None:
        fixed = ClusterManager(_config(mode="fixed"))
        self.assertEqual(fixed.coordinator(), "agent_192_168_88_5")

        clock = Clock(datetime(2024, 1, 1, tzinfo=timezone.utc))
        election = ClusterManager(_config(mode="election"), now_provider=clock)
        self.assertIsNone(election.coordinator())

        election.heartbeat("agent_192_168_88_10", healthy=True)
        election.heartbeat("agent_192_168_88_5", healthy=True)
        self.assertEqual(election.coordinator(), "agent_192_168_88_10")

    def test_queue_idempotency_and_replay_safe_execution(self) -> None:
        manager = ClusterManager(_config())

        first = manager.enqueue_task("ingestion", {"repo": "a"})
        second = manager.enqueue_task("ingestion", {"repo": "a"})
        self.assertEqual(first.task_id, second.task_id)

        running = manager.dequeue_task()
        self.assertIsNotNone(running)
        assert running is not None
        self.assertEqual(running.state, "running")

        completed = manager.execute_task(first.task_id, lambda payload: payload["repo"].upper())
        self.assertEqual(completed.state, "completed")
        self.assertEqual(completed.result, "A")

        replay = manager.execute_task(first.task_id, lambda _: "IGNORED")
        self.assertEqual(replay.result, "A")
        self.assertEqual(replay.attempt, completed.attempt)

    def test_cycle_telemetry_aggregation(self) -> None:
        clock = Clock(datetime(2024, 1, 1, tzinfo=timezone.utc))
        manager = ClusterManager(_config(), now_provider=clock)

        manager.record_cycle_event("cycle-1", "agent_192_168_88_5", "debate started")
        clock.advance(5)
        manager.record_cycle_event(
            "cycle-1",
            "agent_192_168_88_10",
            "debate response emitted",
            metadata={"round": 1},
        )

        logs = manager.cycle_log("cycle-1")
        self.assertEqual(len(logs), 2)
        self.assertEqual(logs[0].message, "debate started")
        self.assertEqual(logs[1].metadata["round"], 1)


if __name__ == "__main__":
    unittest.main()
