"""Entrypoint for orchestrator scaffold."""

from pathlib import Path
import os

from .cluster_manager import ClusterManager


def main() -> None:
    config_path = Path(os.getenv("SWG_CLUSTER_CONFIG", "config/cluster.yaml"))
    manager = ClusterManager(
        config={
            "cluster_control": {
                "coordinator_mode": "fixed",
                "fixed_coordinator_id": os.getenv("SWG_FIXED_COORDINATOR", "agent_192_168_88_5"),
            },
            "nodes": [],
        }
    )
    coordinator = manager.coordinator()
    print(f"[orchestrator] startup with config path: {config_path}")
    print(f"[orchestrator] scaffold coordinator={coordinator}")


if __name__ == "__main__":
    main()
