# Web Chat Deployment Guide

This guide explains how to deploy the SWG web chat server on a LAN host (example host: `192.168.88.10`).

## 1) Required environment variables

Set the following before starting `python -m webapp`.

### Web server bind settings

```bash
export SWG_WEB_HOST=192.168.88.10
export SWG_WEB_PORT=8080
```

- `SWG_WEB_HOST` should be the LAN IP you want other machines to reach.
- `SWG_WEB_PORT` is the listening TCP port (default is `8080`).

### LLM backend configuration

Choose one backend by setting `SWG_LLM_BACKEND`.

#### Mock backend (safe default)

```bash
export SWG_LLM_BACKEND=mock
```

#### OpenAI backend

```bash
export SWG_LLM_BACKEND=openai
export OPENAI_API_KEY=your_api_key
# Optional overrides:
export SWG_OPENAI_BASE_URL=https://api.openai.com/v1
export SWG_OPENAI_MODEL=gpt-4o-mini
```

#### Ollama backend

```bash
export SWG_LLM_BACKEND=ollama
export SWG_OLLAMA_BASE_URL=http://127.0.0.1:11434
# Optional override:
export SWG_OLLAMA_MODEL=llama3.1
```

### Repository/workdir settings

The web chat uses retrieval from the local knowledge index. Keep your workspace paths explicit:

```bash
export SWG_SOURCE_REPO=https://github.com/SWG-Source/dsrc.git
export SWG_WORKDIR=$PWD/.swg-workdir
```

Notes:
- `SWG_SOURCE_REPO` is the upstream source repo that ingestion syncs from.
- `SWG_WORKDIR` stores the cloned source tree and indexed knowledge artifacts.
- If you run the full cluster flow, you may also set `SWG_TARGET_REPO` as needed by sync jobs.

## 2) Startup command

Start the server with:

```bash
python -m webapp
```

Or use the convenience make target:

```bash
make web-chat
```

The service listens on `http://192.168.88.10:8080` when using the example values above.

## 3) Health check

Use this from the server or another LAN machine:

```bash
curl http://192.168.88.10:8080/healthz
```

Expected response:

```json
{"ok": true}
```

## 4) Reverse proxy option (optional)

A reverse proxy is optional, but useful for:
- TLS termination (`https`),
- stable external ports/domains,
- request logging and connection controls.

Example Nginx upstream mapping:

- Nginx listens on `80/443`.
- Proxy traffic to `http://192.168.88.10:8080`.
- Preserve `Host` and forwarding headers.

If you only need simple LAN access and no TLS, direct access to `:8080` is usually sufficient.

## 5) Firewall and LAN networking notes

For other LAN clients to connect:

1. Ensure the app binds to the LAN interface/IP (`SWG_WEB_HOST=192.168.88.10`).
2. Allow inbound TCP `8080` on the host firewall (`ufw`, `firewalld`, cloud SG, etc.).
3. Verify LAN routing/subnet access between clients and `192.168.88.10`.
4. If running in a VM/container, publish/forward port `8080` to the host network.

Quick verification from a second machine:

```bash
curl -i http://192.168.88.10:8080/healthz
```
