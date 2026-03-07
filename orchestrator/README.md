# orchestrator

Runtime coordination layer.

Responsibilities:
- Load cluster configuration.
- Drive stage execution order.
- Apply retry policy and escalation handling.
- Persist run-level status.
- Maintain cluster control-plane state via `orchestrator.cluster_manager`.

## Cluster manager capabilities

`ClusterManager` adds operational control-plane primitives:

- **Node heartbeat/health:** records per-node heartbeats and reports stale/healthy state.
- **Coordinator control:** supports fixed coordinator mode or simple live-node election mode.
- **Task queue:** supports ingestion/debate task enqueue/dequeue lifecycle.
- **Idempotent execution:** deterministic task IDs and replay-safe completion caching.
- **Cycle telemetry aggregation:** structured event logs grouped by debate cycle ID.
