"""Entrypoint for orchestrator scaffold with live runtime console."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
import os
import sys
import time
from typing import TextIO

from .cluster_manager import ClusterManager, TaskRecord, TelemetryEvent

DEFAULT_CONFIG: dict[str, object] = {
    "cluster_control": {
        "coordinator_mode": "fixed",
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


@dataclass(frozen=True)
class RuntimeMetrics:
    """Derived metrics shown by the live console."""

    uptime_seconds: float
    total_tasks: int
    running_tasks: int
    completed_tasks: int
    failed_tasks: int
    success_rate: float
    telemetry_events: int
    events_per_minute: float


def _run_task(payload: dict[str, object]) -> str:
    """Simple deterministic placeholder runner."""
    stage = str(payload.get("stage", "unknown"))
    tick = int(payload.get("tick", 0))
    return f"{stage}-ok-{tick}"


def _compute_metrics(manager: ClusterManager, started_monotonic: float, now_monotonic: float) -> RuntimeMetrics:
    """Compute runtime metrics from task and telemetry state."""
    tasks: tuple[TaskRecord, ...] = tuple(manager._tasks.values())
    total_tasks = len(tasks)
    running_tasks = sum(1 for task in tasks if task.state == "running")
    completed_tasks = sum(1 for task in tasks if task.state == "completed")
    failed_tasks = sum(1 for task in tasks if task.state == "failed")

    attempted = completed_tasks + failed_tasks
    success_rate = completed_tasks / attempted if attempted else 1.0

    all_events = sum(len(events) for events in manager._telemetry.values())
    uptime_seconds = max(0.0, now_monotonic - started_monotonic)
    safe_uptime = max(uptime_seconds, 1.0)
    events_per_minute = (all_events / safe_uptime) * 60

    return RuntimeMetrics(
        uptime_seconds=uptime_seconds,
        total_tasks=total_tasks,
        running_tasks=running_tasks,
        completed_tasks=completed_tasks,
        failed_tasks=failed_tasks,
        success_rate=success_rate,
        telemetry_events=all_events,
        events_per_minute=events_per_minute,
    )


def _status_line(metrics: RuntimeMetrics, coordinator: str | None) -> str:
    """Human-readable single line cluster status."""
    coordinator_text = coordinator or "none"
    return (
        f"coordinator={coordinator_text} | tasks={metrics.total_tasks} "
        f"(run={metrics.running_tasks} done={metrics.completed_tasks} fail={metrics.failed_tasks}) "
        f"| success={metrics.success_rate:.0%} | events/min={metrics.events_per_minute:.2f}"
    )


def _render_console(
    stream: TextIO,
    manager: ClusterManager,
    config_path: Path,
    cycle_id: str,
    started_monotonic: float,
    now_monotonic: float,
    tail_events: int = 8,
) -> None:
    """Render one full console frame with status, metrics, and recent dialogue."""
    metrics = _compute_metrics(manager, started_monotonic, now_monotonic)
    now_display = datetime.now(timezone.utc).isoformat(timespec="seconds")
    health = manager.health_status()
    coordinator = manager.coordinator()
    events: tuple[TelemetryEvent, ...] = manager.cycle_log(cycle_id)

    stream.write("\033[2J\033[H")
    stream.write("SWG-LLM Live Console\n")
    stream.write(f"time={now_display} | config={config_path}\n")
    stream.write(f"uptime={metrics.uptime_seconds:.1f}s | {_status_line(metrics, coordinator)}\n\n")

    stream.write("Node Status\n")
    stream.write("-----------\n")
    for node_id, node in health.items():
        flag = "healthy" if node["healthy"] else "degraded"
        stream.write(
            f"- {node_id:<20} role={node['role']:<18} {flag:<8} "
            f"stale={str(node['stale']).lower():<5} addr={node['address']}\n"
        )

    stream.write("\nConversation + Telemetry\n")
    stream.write("------------------------\n")
    if not events:
        stream.write("(no events yet; waiting for debate activity)\n")
    else:
        for event in events[-tail_events:]:
            meta = f" | meta={event.metadata}" if event.metadata else ""
            stream.write(
                f"[{event.timestamp.isoformat(timespec='seconds')}] "
                f"{event.node_id} {event.level}: {event.message}{meta}\n"
            )
    stream.flush()


def run_runtime_console(
    manager: ClusterManager,
    config_path: Path,
    refresh_seconds: float,
    max_ticks: int = 0,
    stream: TextIO = sys.stdout,
    sleep_fn: callable = time.sleep,
    monotonic_fn: callable = time.monotonic,
) -> None:
    """Run an endless runtime loop until interrupted (or max_ticks in tests)."""
    cycle_id = os.getenv("SWG_CYCLE_ID", "live-cycle")
    started = monotonic_fn()

    tick = 0
    while True:
        tick += 1
        for node_id in manager._nodes:
            manager.heartbeat(node_id, healthy=True, details={"tick": tick})

        speaker = "agent_192_168_88_5" if tick % 2 else "agent_192_168_88_10"
        manager.record_cycle_event(
            cycle_id=cycle_id,
            node_id=speaker,
            message="proposal emitted" if tick % 2 else "review completed",
            metadata={"tick": tick, "phase": "debate" if tick % 2 else "review"},
        )

        stage = "ingestion" if tick % 2 else "debate"
        task = manager.enqueue_task(stage, {"stage": stage, "tick": tick})
        manager.execute_task(task.task_id, _run_task)

        _render_console(
            stream=stream,
            manager=manager,
            config_path=config_path,
            cycle_id=cycle_id,
            started_monotonic=started,
            now_monotonic=monotonic_fn(),
        )

        if max_ticks > 0 and tick >= max_ticks:
            break
        sleep_fn(refresh_seconds)


def main() -> None:
    config_path = Path(os.getenv("SWG_CLUSTER_CONFIG", "config/cluster.yaml"))
    refresh_seconds = float(os.getenv("SWG_CONSOLE_REFRESH_SECONDS", "2"))
    max_ticks = int(os.getenv("SWG_MAX_TICKS", "0"))

    manager = ClusterManager(config=DEFAULT_CONFIG)

    print(f"[orchestrator] startup with config path: {config_path}")
    print("[orchestrator] live console enabled; press Ctrl+C to stop")

    try:
        run_runtime_console(
            manager=manager,
            config_path=config_path,
            refresh_seconds=refresh_seconds,
            max_ticks=max_ticks,
        )
    except KeyboardInterrupt:
        print("\n[orchestrator] graceful shutdown requested")


if __name__ == "__main__":
    main()
