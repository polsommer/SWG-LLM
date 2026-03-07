# SWG-LLM Cluster Scaffold

This repository provides a starter scaffold for a multi-node LLM workflow that coordinates ingestion, agent execution, consensus, and publication to a target Git repository.

## Project Layout

- `orchestrator/` - runtime coordination logic for stage ordering, retries, and state transitions.
- `agents/` - node-specific agent behavior (`agent_192_168_88_5`, `agent_192_168_88_10`).
- `ingestion/` - repository cloning, parsing, embedding, and indexing pipeline hooks.
- `consensus/` - debate/refinement orchestration and disagreement resolution flow.
- `sync/` - output publishing and synchronization with a target Git repository.
- `config/cluster.yaml` - cluster topology, roles, retry policy, and debate rounds.

## Startup

1. Duplicate and edit `config/cluster.yaml` as needed for your environment.
2. Export required environment variables.
3. Start orchestration entrypoint (placeholder in this scaffold):

```bash
python -m orchestrator
```

> This scaffold provides structure and starter modules. Add concrete runtime integrations for your preferred model/runtime stack.

## Environment Variables

- `SWG_CLUSTER_CONFIG` - path to cluster config file (default: `config/cluster.yaml`).
- `SWG_SOURCE_REPO` - source repository URL or local path for ingestion.
- `SWG_TARGET_REPO` - target repository URL for sync publishing.
- `SWG_WORKDIR` - local working directory for cloned repositories and intermediate artifacts.
- `SWG_EMBEDDING_MODEL` - embedding model identifier for ingestion/indexing.
- `SWG_MAX_RETRIES` - optional override for retry attempts.
- `SWG_LOG_LEVEL` - logging verbosity (`DEBUG`, `INFO`, `WARN`, `ERROR`).

## Failure Handling

The scaffold is organized to make failures explicit and recoverable:

- **Orchestrator-level retries:** use retry policy from cluster config to restart failed stages.
- **Stage-level isolation:** ingestion, consensus, and sync live in separate modules to support targeted re-runs.
- **Agent disagreement handling:** consensus module should record disagreements and gate sync until resolution criteria are met.
- **Sync safeguards:** publish step should be idempotent and use branch/commit checks before forceful updates.
- **Auditability:** each module should emit structured logs with run IDs to support root-cause analysis.

Implement production-specific alerting, checkpointing, and rollback behavior as a next step.
