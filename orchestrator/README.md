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

## Task orchestration layer

`orchestrator.orchestration_layer` adds execution pipeline primitives for long-running workflows:

- **Planner stage:** converts free-form intent into explicit subtasks with tool mapping.
- **Typed tool adapters:** search, DB read, API call, and code action adapters validate typed input/output schemas.
- **Verifier stage:** checks completeness, policy safety, and factual signal consistency; emits confidence.
- **Bounded retries:** execution retries when verification fails until `max_attempts` is reached.
- **Stop conditions:** auto-complete requires verification pass **and** confidence threshold.
- **Persistence/resume:** task state and traces are persisted to JSON for safe workflow continuation.
- **Execution traces:** stage-by-stage trace events support observability/debugging.
