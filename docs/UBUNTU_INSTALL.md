# Ubuntu Installation Guide

This guide gets `SWG-LLM` running on Ubuntu with a local Ollama model.

It covers:

- system packages
- Python virtual environment setup
- Ollama installation
- running the main FastAPI workspace app
- optional ingestion and retrieval chat setup
- optional `systemd` service setup

## 1. Install system packages

Update package metadata and install the basics:

```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-pip git curl make
```

Optional but useful:

```bash
sudo apt install -y build-essential
```

## 2. Clone the repository

Choose a working location and clone the repo:

```bash
git clone <your-repo-url> swg-llm
cd swg-llm
```

If you already have the repo, just `cd` into it.

## 3. Create a virtual environment

From the repository root:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Every new shell session should reactivate the environment:

```bash
cd /path/to/swg-llm
source .venv/bin/activate
```

## 4. Install Ollama

Install Ollama with its Linux installer:

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

Start or verify the Ollama service:

```bash
systemctl --user enable --now ollama
systemctl --user status ollama
```

If your setup does not use a user service, you can run it manually in another terminal:

```bash
ollama serve
```

Pull a model that fits the project well:

```bash
ollama pull qwen2.5:7b-instruct-q4_K_M
```

Other models mentioned by this repo:

- `mistral:7b-instruct-q4_K_M`
- `phi3:mini`

## 5. Run the main workspace app

This is the primary UI in the repository.

Start it with:

```bash
source .venv/bin/activate
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Then open:

```text
http://127.0.0.1:8000
```

If you want other machines on your LAN to reach it, bind to all interfaces instead:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Then allow the port through your firewall if needed:

```bash
sudo ufw allow 8000/tcp
```

## 6. Verify the app is healthy

From another terminal:

```bash
curl http://127.0.0.1:8000/api/health
```

You should get a JSON response with `ok: true` plus model, memory, and project index status.

## 7. Optional: build the local project index

The repo includes an ingestion pipeline for retrieval over source files.

Set a work directory:

```bash
export SWG_WORKDIR="$PWD/.swg-workdir"
```

Run a one-time ingest:

```bash
python -m ingestion ingest
```

Or:

```bash
make ingest
```

To test retrieval:

```bash
python -m ingestion ask "How does orchestration and consensus work?" --top-k 3
```

To keep ingesting on a timer:

```bash
python -m ingestion auto-ingest --interval-seconds 300
```

## 8. Optional: run the smaller retrieval web chat

The repository also includes a lightweight web chat server under `webapp/`.

Set its environment:

```bash
export SWG_WORKDIR="$PWD/.swg-workdir"
export SWG_WEB_HOST=127.0.0.1
export SWG_WEB_PORT=8080
export SWG_LLM_BACKEND=ollama
export SWG_OLLAMA_BASE_URL=http://127.0.0.1:11434
export SWG_OLLAMA_MODEL=qwen2.5:7b-instruct-q4_K_M
```

Start it with:

```bash
python -m webapp
```

Or:

```bash
make web-chat
```

Open:

```text
http://127.0.0.1:8080
```

Health check:

```bash
curl http://127.0.0.1:8080/healthz
```

## 9. Optional: run the main app with `systemd`

If you want the FastAPI app to restart automatically on boot, create a user service.

Create `~/.config/systemd/user/swg-llm.service`:

```ini
[Unit]
Description=SWG-LLM FastAPI app
After=network.target

[Service]
WorkingDirectory=/path/to/swg-llm
Environment="PATH=/path/to/swg-llm/.venv/bin"
ExecStart=/path/to/swg-llm/.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
```

Reload and start it:

```bash
systemctl --user daemon-reload
systemctl --user enable --now swg-llm
systemctl --user status swg-llm
```

View logs:

```bash
journalctl --user -u swg-llm -f
```

## 10. Common Ubuntu issues

### `python3 -m venv` fails

Install the venv package:

```bash
sudo apt install -y python3-venv
```

### `ollama` command is missing

Confirm the installer completed successfully:

```bash
which ollama
ollama --version
```

If needed, rerun:

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### The UI loads but the model does not answer

Check that Ollama is running and that the model exists:

```bash
curl http://127.0.0.1:11434/api/tags
ollama list
```

If the model is missing:

```bash
ollama pull qwen2.5:7b-instruct-q4_K_M
```

### Port `8000` or `8080` is already in use

Find the process:

```bash
ss -ltnp | grep ':8000\|:8080'
```

Then either stop the conflicting process or start this app on a different port.

### Ingestion returns weak or empty results

- Make sure `SWG_WORKDIR` is set.
- Run `python -m ingestion ingest` again.
- Try a larger retrieval count such as `--top-k 5`.

## 11. Quick start summary

If you just want the shortest path:

```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-pip git curl make
git clone <your-repo-url> swg-llm
cd swg-llm
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
curl -fsSL https://ollama.com/install.sh | sh
ollama pull qwen2.5:7b-instruct-q4_K_M
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Then open `http://127.0.0.1:8000`.
