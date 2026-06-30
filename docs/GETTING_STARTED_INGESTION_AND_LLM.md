# Getting Started: Ingest Data and Run Your LLM with SWG-LLM

This guide gives you a practical path from **zero setup** to a working flow where:
1. your source repository is ingested into the local knowledge index,
2. agents can retrieve relevant context, and
3. your LLM can use that context to produce grounded answers.

---

## 1) Prerequisites

From the repository root:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Set a working directory and source repository (example):

```bash
export SWG_WORKDIR=$PWD/.swg-workdir
export SWG_SOURCE_REPO=https://github.com/SWG-Source/dsrc.git
```

> Note: the current scaffold defaults to `https://github.com/SWG-Source/dsrc.git` inside `RepositoryIngestor`.

---

## 2) Ingest data (clone/pull + chunk + embed + index)

Run this one-off ingestion command:

```bash
python -m ingestion ingest
```

Or with Make:

```bash
make ingest
```

What `refresh()` does under the hood:
- syncs the source repo locally (`clone` or `pull`),
- chunks supported files (`.md`, `.txt`, `.py`, `.yaml`, etc.),
- computes deterministic local embeddings,
- writes the semantic index at `$SWG_WORKDIR/knowledge/index.json`.

If nothing changed in the source repo revision, it returns `status: unchanged` and skips re-embedding.

---

## 3) Validate retrieval quickly

Test semantic retrieval directly:

```bash
python -m ingestion ask "How does orchestration and consensus work?" --top-k 3
```

Or with Make:

```bash
make ask Q="How does orchestration and consensus work?"
```

If results are empty or weak:
- run `refresh()` again,
- confirm your source repo has supported file types,
- increase `top_k`.

---


## 3.5) Keep auto-updating with git pull + re-ingest

`refresh()` already performs `git fetch` + `git pull --ff-only` through `RepositoryIngestor.sync()`.
Use auto-ingest mode to keep learning from new commits continuously:

```bash
python -m ingestion auto-ingest --interval-seconds 300
```

Or with Make:

```bash
make auto-ingest INTERVAL=300
```

Use `--max-cycles` for bounded runs/tests:

```bash
python -m ingestion auto-ingest --interval-seconds 30 --max-cycles 5
```

---

## 4) Easy way to get your LLM working (RAG pattern)

Use retrieval as context before you call your LLM API.

### Minimal Python pattern

```python
from ingestion.query_interface import KnowledgeQueryService


def build_context(question: str, top_k: int = 5) -> str:
    service = KnowledgeQueryService()
    docs = service.query(question, top_k=top_k)
    blocks = []
    for d in docs:
        blocks.append(
            f"Source: {d.file_path}:{d.start_line}-{d.end_line}\n{d.text}"
        )
    return "\n\n---\n\n".join(blocks)


question = "What are the orchestrator responsibilities?"
context = build_context(question)

prompt = f"""
You are an assistant for the SWG-LLM project.
Answer the user using ONLY the context below.
If the answer is not present, say you are not sure.

Context:
{context}

Question:
{question}
""".strip()

# send `prompt` to your preferred LLM provider (OpenAI, local model, etc.)
# print(llm_response)
```

This is the quickest reliable architecture:
1. call `refresh()` on startup or schedule,
2. call `query(question, top_k)` per user request,
3. inject retrieved chunks into your model prompt,
4. return answer + source citations.

---

## 5) Plug retrieval into existing agents

Both agent tool modules already expose:

```python
retrieve_context(question: str, top_k: int = 5) -> list[dict[str, object]]
```

Use this as your tool/function call in agent prompts so each debating agent fetches shared context before proposing or reviewing output.

---

## 6) Run the orchestrator loop

```bash
export SWG_CLUSTER_CONFIG=config/cluster.yaml
export SWG_CONSOLE_REFRESH_SECONDS=2
export SWG_MAX_TICKS=20
python -m orchestrator
```

The console shows node health, cycle telemetry, and task execution progress.

---

## 7) Operational checklist (recommended)

- Run ingestion once at startup:
  - `KnowledgeQueryService().refresh()`
- Re-ingest on a schedule (cron/systemd/Git webhook).
- Keep `SWG_WORKDIR` on persistent disk.
- Track index growth (`index.json` size/chunk count).
- Add model-specific env vars (API key/model name) in your runtime, not in source control.

---

## 8) Common issues and fixes

- **`git clone` fails**: check repo URL/auth and outbound network access.
- **No retrieval results**: ingest first (`refresh()`), verify file extensions are supported.
- **Stale answers**: source repo changed but index not refreshed — run ingestion again.
- **Weak relevance**: increase `top_k`, adjust chunking in `ChunkIndexer` (`chunk_lines`, `overlap_lines`).

---

## 9) What to build next

If you want production-quality results, next upgrades are:
- replace hashing embeddings with a real embedding model,
- store vectors in a dedicated vector DB,
- add reranking before prompt assembly,
- enforce answer citations from retrieved chunks.
