# LocalAgent 1660

LocalAgent 1660 is a local AI workspace for the Star Wars Galaxies codebase. It now leans closer to an Odysseus-style self-hosted workspace experience while staying lightweight enough for local use on a GTX 1660 Super:

- chat with a local model
- upload files into a workspace
- search and summarize uploaded content
- generate new files automatically
- keep persistent lessons and auto-captured knowledge notes
- continuously index and re-learn the SWG repo structure

It is designed to run on a GTX 1660 Super by using a quantized 7B-class model through Ollama.

## What it can do

- Ingest `.txt`, `.md`, `.py`, `.js`, `.json`, `.html`, `.css`, `.csv`, and other text-like files
- Store uploaded files in a workspace
- Build simple searchable file context
- Generate documents or code files into the workspace
- Maintain a lightweight reflection memory based on previous runs and user feedback
- Use a small autonomous tool loop for listing files, reading files, and writing generated outputs
- Run small Python tasks in a local sandbox with time limits and output isolation
- Automatically ingest `swg-main/src` and `swg-main/dsrc` into a local project index
- Poll project roots in the background and auto-reindex when source files change
- Extract richer code structure such as imports, inheritance, function signatures, and call hints
- Use repo-aware answer planning so codebase questions search first, inspect likely files, then answer with clearer evidence
- Build a cross-file code graph so symbols can be traced across definitions, references, calls, and inheritance
- Use lightweight semantic retrieval so conceptually related code can still be found even when exact names differ
- Show an Odysseus-inspired workspace UI with model status, memory deck, research output, and repo intelligence panels
- Auto-capture lightweight knowledge notes from successful runs so the workspace accumulates reusable context

## What "self improving" means here

This project does **not** let the AI silently rewrite its own code. Instead, it improves safely by:

- saving successful patterns and lessons
- recording user feedback
- using recent lessons in later prompts
- tracking which generated files were created and why
- storing simple timestamped knowledge observations from successful research and generation runs

That gives you practical improvement without unsafe autonomous behavior.

## Autonomous tool use

The chat agent can now choose from a small safe toolset during a request:

- `list_files`
- `read_file`
- `write_file`
- `run_python`
- `run_python_script`
- `index_project`
- `search_project`
- `read_project_file`

The loop is intentionally narrow:

- it can only read from `uploads/` and `generated/`
- it can only write into `generated/`
- it executes at most a few tool steps per request
- tool activity is shown in the UI so you can see what happened

## Code execution sandbox

The project now includes a guarded local Python runner for small tasks such as:

- reading uploaded text or CSV-like files copied into the sandbox
- generating derived output files
- testing tiny scripts before saving results

Current guardrails:

- execution uses an isolated run folder under `data/sandboxes/`
- reads are limited to sandbox-local files during execution
- writes are limited to the sandbox `output/` folder
- output files are copied into `data/generated/sandbox_runs/...`
- each run has a short timeout
- imports are limited to a small allowlist of data-oriented standard-library modules
- obviously risky calls such as process spawning and socket access are rejected before execution
- only Python execution is supported right now

Important note:

This is a practical local safety layer with best-effort restrictions, not a hardened security boundary against hostile code. It is appropriate for your own trusted tasks, prototypes, and agent workflows on a personal machine.

If you need to execute untrusted code, the next step is a stronger boundary such as VM-, container-, or OS-isolated execution outside this in-process app model.

## Project inference

The agent now auto-ingests these code roots:

- `swg-main/src`
- `swg-main/dsrc`

It stores a local project index with:

- file manifest data
- code chunks
- lightweight symbol extraction
- imports and includes
- inheritance hints
- function signatures
- simple call/reference hints
- a cross-file symbol and file relationship graph
- semantic chunk vectors for hybrid retrieval
- searchable snippets for retrieval during chat

That gives the model a much better chance of answering repo questions without loading the whole codebase into one prompt.

## Background reindexing

The app now runs a lightweight background monitor for:

- `swg-main/src`
- `swg-main/dsrc`

It periodically checks for file path, size, or modification-time changes and automatically rebuilds the project index when it detects updates.

The UI shows:

- current monitor state
- last scan time
- last detected change
- last automatic reindex time
- any background error
- current local model availability
- memory counts and recent knowledge observations
- whether the repo is using deep indexing or large-repo fast mode

## Hardware target

Recommended model choices for a GTX 1660 Super 6GB:

- `qwen2.5:7b-instruct-q4_K_M`
- `mistral:7b-instruct-q4_K_M`
- `phi3:mini`

Best default:

- `qwen2.5:7b-instruct-q4_K_M`

## Quick start

1. Install Python 3.11+
2. Install Ollama from [https://ollama.com](https://ollama.com)
3. Pull a model:

```powershell
ollama pull qwen2.5:7b-instruct-q4_K_M
```

4. Create a virtual environment and install dependencies:

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

5. Run the app:

```powershell
uvicorn app.main:app --reload
```

Or on this machine:

```powershell
.\run_local_agent.ps1
```

To keep the local server alive in the background on Windows:

```powershell
.\run_local_agent_background.ps1
```

6. Open:

```text
http://127.0.0.1:8000
```

## Project structure

```text
local_agent1660/
  app/
    main.py
    agent.py
    models.py
    storage.py
    prompts.py
  static/
    index.html
    styles.css
    app.js
  data/
    uploads/
    generated/
    memory/
```

## Notes

- The agent uses Ollama over HTTP at `http://127.0.0.1:11434`
- Uploaded files are stored locally
- Generated files are written to `data/generated/`
- Reflection notes are stored in `data/memory/`

## Next upgrades you can add

- better embeddings and semantic search
- tool-use plugins
- task queue and background workers
- multi-agent planner
- Git integration
