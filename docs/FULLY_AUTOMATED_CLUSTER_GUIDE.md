# Full Guide: Fully Automatic SWG-LLM Cluster (Ingest → Review → Learn Code → Repeat)

This runbook shows how to run SWG-LLM as a **continuous cluster workflow** where the system:

1. runs in orchestrator/cluster mode,
2. continuously ingests repository updates,
3. reviews retrieved code context,
4. learns from new commits by rebuilding the local knowledge index, and
5. repeats forever.

---

## 1) What “fully automatic” means in this scaffold

In this repository, “fully automatic” is achieved by combining:

- the orchestrator runtime loop (`python -m orchestrator`),
- the ingestion auto-loop (`python -m ingestion auto-ingest`), and
- process supervision (systemd) so both restart automatically on reboot/failure.

The orchestrator gives you clustered coordination and cycle telemetry, while ingestion keeps the knowledge index current so agents can retrieve fresh code context.

---

## 2) One-time host setup (Ubuntu/Debian)

From a clean machine:

```bash
sudo apt update
sudo apt install -y git python3 python3-venv python3-pip
```

Clone and install:

```bash
git clone https://github.com/polsommer/SWG-LLM.git /opt/SWG-LLM
cd /opt/SWG-LLM
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Create runtime directories:

```bash
sudo mkdir -p /var/lib/swg-llm/workdir
sudo mkdir -p /var/log/swg-llm
sudo chown -R "$USER":"$USER" /var/lib/swg-llm /var/log/swg-llm
```

---

## 3) Configure cluster mode

Create a local cluster config:

```bash
cp config/cluster.yaml config/cluster.local.yaml
```

Edit `config/cluster.local.yaml` for your desired topology (node list, roles, retries, debate rounds).

Use this config at runtime with:

```bash
export SWG_CLUSTER_CONFIG=/opt/SWG-LLM/config/cluster.local.yaml
```

---

## 4) Configure environment for automatic ingest + learning

Create a shared env file:

```bash
cat >/opt/SWG-LLM/.env.cluster <<'ENV'
SWG_CLUSTER_CONFIG=/opt/SWG-LLM/config/cluster.local.yaml
SWG_SOURCE_REPO=https://github.com/SWG-Source/dsrc.git
SWG_TARGET_REPO=/opt/SWG-LLM/.target-repo
SWG_WORKDIR=/var/lib/swg-llm/workdir
SWG_LOG_LEVEL=INFO
SWG_CONSOLE_REFRESH_SECONDS=2
SWG_MAX_TICKS=0
ENV
```

Notes:

- `SWG_MAX_TICKS=0` means continuous execution.
- `SWG_SOURCE_REPO` is what ingestion continuously pulls from and re-indexes.
- The index is written under `$SWG_WORKDIR/knowledge/index.json`.

---

## 5) Validate manually before enabling automation

Run one ingestion cycle:

```bash
cd /opt/SWG-LLM
source .venv/bin/activate
set -a; source .env.cluster; set +a
python -m ingestion ingest
```

Run a retrieval check:

```bash
python -m ingestion ask "How does orchestration and consensus work?" --top-k 3
```

Run orchestrator in cluster loop mode:

```bash
python -m orchestrator
```

If all three work, continue to systemd automation.

---

## 6) Run fully automatic with systemd

### 6.1 Auto-ingest service (learn new code repeatedly)

Create `/etc/systemd/system/swg-ingest.service`:

```ini
[Unit]
Description=SWG-LLM Auto Ingestion Loop
After=network.target

[Service]
Type=simple
User=%i
WorkingDirectory=/opt/SWG-LLM
EnvironmentFile=/opt/SWG-LLM/.env.cluster
ExecStart=/opt/SWG-LLM/.venv/bin/python -m ingestion auto-ingest --interval-seconds 300
Restart=always
RestartSec=5
StandardOutput=append:/var/log/swg-llm/ingest.log
StandardError=append:/var/log/swg-llm/ingest.log

[Install]
WantedBy=multi-user.target
```

### 6.2 Orchestrator service (cluster runtime loop)

Create `/etc/systemd/system/swg-orchestrator.service`:

```ini
[Unit]
Description=SWG-LLM Orchestrator Cluster Loop
After=network.target swg-ingest.service

[Service]
Type=simple
User=%i
WorkingDirectory=/opt/SWG-LLM
EnvironmentFile=/opt/SWG-LLM/.env.cluster
ExecStart=/opt/SWG-LLM/.venv/bin/python -m orchestrator
Restart=always
RestartSec=5
StandardOutput=append:/var/log/swg-llm/orchestrator.log
StandardError=append:/var/log/swg-llm/orchestrator.log

[Install]
WantedBy=multi-user.target
```

### 6.3 Enable and start

Replace `<linux-user>` with your runtime user:

```bash
sudo systemctl daemon-reload
sudo systemctl enable swg-ingest@swg-node1.service
sudo systemctl enable swg-orchestrator@swg-node1.service
sudo systemctl start swg-ingest@swg-node1.service
sudo systemctl start swg-orchestrator@swg-node1.service
```

Check status:

```bash
systemctl status swg-ingest@swg-node1.service --no-pager
systemctl status swg-orchestrator@swg-node1.service --no-pager
```

Tail logs:

```bash
tail -f /var/log/swg-llm/ingest.log /var/log/swg-llm/orchestrator.log
```

---

## 7) The repeat cycle (ingest → review → learn → repeat)

Your automated loop now behaves like this:

1. `ingestion auto-ingest` pulls latest commits from `SWG_SOURCE_REPO`.
2. Changed files are chunked/embedded/indexed into local knowledge storage.
3. Cluster agents/orchestrator retrieve context from updated index.
4. Consensus/debate/refinement can review outputs against current code context.
5. Process repeats on the configured interval forever.

This gives continuous codebase learning without manual restarts.

---

## 8) Operations checklist

Use this quick checklist for reliable long-running operation:

- Keep `/var/lib/swg-llm/workdir` on persistent storage.
- Monitor growth of `knowledge/index.json`.
- Keep `SWG_SOURCE_REPO` reachable/authenticated.
- Keep both services in `Restart=always` mode.
- Run periodic retrieval spot-checks with `python -m ingestion ask ...`.
- Back up your cluster config (`config/cluster.local.yaml`).

---

## 9) Common production patterns

- **Fast-learning mode:** set `--interval-seconds 60` for near-real-time ingest.
- **Balanced mode:** `--interval-seconds 300` (recommended default).
- **Low-cost mode:** `--interval-seconds 900` for larger repos with lower churn.
- **Canary mode:** one orchestrator instance with reduced node set for validation before full rollout.

---

## 10) Troubleshooting

- **Service keeps restarting:** run command manually from `/opt/SWG-LLM` with `.env.cluster` loaded to capture stack trace.
- **No new learning detected:** verify upstream repo changed and auto-ingest interval elapsed.
- **Weak review quality:** increase retrieval `--top-k` during evaluation and tune chunking settings in ingestion components.
- **No orchestrator activity:** verify `SWG_CLUSTER_CONFIG` path and YAML validity.

