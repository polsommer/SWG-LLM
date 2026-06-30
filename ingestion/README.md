# ingestion

Pipeline stage for repository cloning, parsing, embedding, and indexing.

Implemented modules:
- `repository_ingestor.py` (`RepositoryIngestor`) clones/pulls `https://github.com/SWG-Source/dsrc.git` into a controlled local workspace.
- `chunk_indexer.py` (`ChunkIndexer`) walks supported source/doc file extensions and emits overlap-aware chunks enriched with metadata (`document_title`, `section`, `last_updated`, `access_scope`, `source_kind`).
- `knowledge_store.py` (`KnowledgeStore`) computes deterministic hashing embeddings, persists vectors, tracks revision hashes, and performs hybrid retrieval (semantic + BM25 keyword scoring) followed by reranking.
- `query_interface.py` (`KnowledgeQueryService`) exposes a shared retrieval interface used by both debating agents.

## RAG behavior

The retrieval-augmented generation flow now:
1. Indexes high-value repository knowledge sources (docs/code/wiki/log-like files) into a vector+keyword index.
2. Adds metadata to every chunk so downstream prompts can preserve provenance and access scope.
3. Runs hybrid retrieval and reranking before answer generation.
4. Logs retrieval telemetry (`retrieval_metrics.jsonl`) for hit/miss analysis.

Consensus generation consumes retrieved chunks with strict token budgeting and produces grounded/abstaining outputs when evidence is missing.

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
