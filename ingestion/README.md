# ingestion

Pipeline stage for repository cloning, parsing, embedding, and indexing.

Implemented modules:
- `repository_ingestor.py` (`RepositoryIngestor`) clones/pulls `https://github.com/SWG-Source/dsrc.git` into a controlled local workspace.
- `chunk_indexer.py` (`ChunkIndexer`) walks supported source/doc file extensions and emits overlap-aware chunks.
- `knowledge_store.py` (`KnowledgeStore`) computes deterministic hashing embeddings, persists vectors, tracks revision hashes, and incrementally reuses unchanged chunks.
- `query_interface.py` (`KnowledgeQueryService`) exposes a shared retrieval interface used by both debating agents.


## Quick CLI

Use the ingestion module directly from the command line:

```bash
python -m ingestion ingest
python -m ingestion ask "What are orchestrator responsibilities?" --top-k 5
python -m ingestion auto-ingest --interval-seconds 300
```

You can also use Make shortcuts from the repo root:

```bash
make ingest
make ask Q="What are orchestrator responsibilities?"
make auto-ingest INTERVAL=300
```
