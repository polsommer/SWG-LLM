"""Cluster control-plane manager for health, coordination, and task orchestration."""

from __future__ import annotations

from collections import defaultdict, deque
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
import hashlib
from typing import Any, Callable


@dataclass(frozen=True)
class ClusterNode:
    """Node metadata loaded from cluster configuration."""

    node_id: str
    address: str
    role: str
    enabled: bool = True
    health_endpoint: str | None = None


@dataclass
class NodeHeartbeat:
    """Latest heartbeat and derived liveness signal for a node."""

    node_id: str
    healthy: bool
    timestamp: datetime
    details: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class TaskRecord:
    """Immutable snapshot of task lifecycle state."""

    task_id: str
    task_type: str
    payload: dict[str, Any]
    state: str
    attempt: int
    result: Any = None
    error: str | None = None


@dataclass(frozen=True)
class TelemetryEvent:
    """Telemetry event produced during a debate cycle."""

    cycle_id: str
    timestamp: datetime
    node_id: str
    level: str
    message: str
    metadata: dict[str, Any] = field(default_factory=dict)


class ClusterManager:
    """Coordinates cluster liveness, leader selection, queued tasks, and telemetry."""

    def __init__(self, config: dict[str, Any], now_provider: Callable[[], datetime] | None = None) -> None:
        control = config.get("cluster_control", {})
        self._mode = control.get("coordinator_mode", "fixed")
        self._fixed_coordinator_id = control.get("fixed_coordinator_id")
        self._heartbeat_ttl = timedelta(seconds=int(control.get("heartbeat_ttl_seconds", 30)))

        self._now_provider = now_provider or (lambda: datetime.now(timezone.utc))

        self._nodes: dict[str, ClusterNode] = {}
        for raw in config.get("nodes", []):
            node_id = raw.get("agent_id") or raw.get("address")
            if not node_id:
                continue
            self._nodes[node_id] = ClusterNode(
                node_id=node_id,
                address=raw["address"],
                role=raw.get("role", "worker"),
                enabled=bool(raw.get("enabled", True)),
                health_endpoint=raw.get("health_endpoint"),
            )

        self._heartbeats: dict[str, NodeHeartbeat] = {}
        self._queue: deque[str] = deque()
        self._tasks: dict[str, TaskRecord] = {}
        self._telemetry: dict[str, list[TelemetryEvent]] = defaultdict(list)

    def heartbeat(self, node_id: str, healthy: bool = True, details: dict[str, Any] | None = None) -> NodeHeartbeat:
        """Record heartbeat/health status for a node."""
        beat = NodeHeartbeat(
            node_id=node_id,
            healthy=healthy,
            timestamp=self._now_provider(),
            details=details or {},
        )
        self._heartbeats[node_id] = beat
        return beat

    def health_status(self) -> dict[str, dict[str, Any]]:
        """Return a node health map including stale-heartbeat detection."""
        now = self._now_provider()
        status: dict[str, dict[str, Any]] = {}
        for node_id, node in self._nodes.items():
            beat = self._heartbeats.get(node_id)
            stale = True
            healthy = False
            timestamp = None
            details: dict[str, Any] = {}
            if beat is not None:
                stale = (now - beat.timestamp) > self._heartbeat_ttl
                healthy = beat.healthy and not stale
                timestamp = beat.timestamp.isoformat()
                details = beat.details

            status[node_id] = {
                "enabled": node.enabled,
                "address": node.address,
                "role": node.role,
                "health_endpoint": node.health_endpoint,
                "healthy": healthy,
                "stale": stale,
                "last_heartbeat": timestamp,
                "details": details,
            }
        return status

    def coordinator(self) -> str | None:
        """Resolve coordinator by fixed mode or heartbeat-driven election."""
        if self._mode == "fixed":
            return self._fixed_coordinator_id

        live_nodes: list[str] = []
        for node_id, state in self.health_status().items():
            if state["enabled"] and state["healthy"]:
                live_nodes.append(node_id)
        if not live_nodes:
            return None
        return sorted(live_nodes)[0]

    def make_task_id(self, task_type: str, payload: dict[str, Any]) -> str:
        """Generate deterministic idempotency key for a task request."""
        payload_repr = repr(sorted(payload.items()))
        digest = hashlib.sha256(f"{task_type}|{payload_repr}".encode("utf-8")).hexdigest()
        return digest[:16]

    def enqueue_task(self, task_type: str, payload: dict[str, Any], task_id: str | None = None) -> TaskRecord:
        """Push unique task into queue and return current record snapshot."""
        resolved_id = task_id or self.make_task_id(task_type, payload)
        existing = self._tasks.get(resolved_id)
        if existing is not None:
            return existing

        record = TaskRecord(
            task_id=resolved_id,
            task_type=task_type,
            payload=payload,
            state="pending",
            attempt=0,
        )
        self._tasks[resolved_id] = record
        self._queue.append(resolved_id)
        return record

    def dequeue_task(self) -> TaskRecord | None:
        """Pop next pending task and mark as running."""
        while self._queue:
            task_id = self._queue.popleft()
            record = self._tasks[task_id]
            if record.state != "pending":
                continue
            running = TaskRecord(
                task_id=record.task_id,
                task_type=record.task_type,
                payload=record.payload,
                state="running",
                attempt=record.attempt + 1,
            )
            self._tasks[task_id] = running
            return running
        return None

    def execute_task(self, task_id: str, runner: Callable[[dict[str, Any]], Any]) -> TaskRecord:
        """Execute a task once; already-completed tasks return cached result safely."""
        record = self._tasks[task_id]
        if record.state == "completed":
            return record
        if record.state == "pending":
            record = self.dequeue_task() or record

        try:
            result = runner(record.payload)
            completed = TaskRecord(
                task_id=record.task_id,
                task_type=record.task_type,
                payload=record.payload,
                state="completed",
                attempt=record.attempt,
                result=result,
            )
            self._tasks[task_id] = completed
            return completed
        except Exception as exc:  # noqa: BLE001
            failed = TaskRecord(
                task_id=record.task_id,
                task_type=record.task_type,
                payload=record.payload,
                state="failed",
                attempt=record.attempt,
                error=str(exc),
            )
            self._tasks[task_id] = failed
            return failed

    def record_cycle_event(
        self,
        cycle_id: str,
        node_id: str,
        message: str,
        level: str = "INFO",
        metadata: dict[str, Any] | None = None,
    ) -> TelemetryEvent:
        """Append telemetry for one debate cycle."""
        event = TelemetryEvent(
            cycle_id=cycle_id,
            timestamp=self._now_provider(),
            node_id=node_id,
            level=level,
            message=message,
            metadata=metadata or {},
        )
        self._telemetry[cycle_id].append(event)
        return event

    def cycle_log(self, cycle_id: str) -> tuple[TelemetryEvent, ...]:
        """Get immutable ordered telemetry events for a cycle."""
        events = sorted(self._telemetry.get(cycle_id, []), key=lambda item: item.timestamp)
        return tuple(events)

    def task_record(self, task_id: str) -> TaskRecord:
        """Return current task snapshot."""
        return self._tasks[task_id]
