"""Entrypoint for orchestrator scaffold."""

from pathlib import Path
import os


def main() -> None:
    config_path = Path(os.getenv("SWG_CLUSTER_CONFIG", "config/cluster.yaml"))
    print(f"[orchestrator] scaffold startup with config: {config_path}")


if __name__ == "__main__":
    main()
