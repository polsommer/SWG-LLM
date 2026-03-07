# ingestion

Pipeline stage for repository cloning, parsing, embedding, and indexing.

Implemented modules:
- `repository_ingestor.py` (`RepositoryIngestor`) clones/pulls `https://github.com/SWG-Source/dsrc.git` into a controlled local workspace.
- `chunk_indexer.py` (`ChunkIndexer`) walks supported source/doc file extensions and emits overlap-aware chunks.
- `knowledge_store.py` (`KnowledgeStore`) computes deterministic hashing embeddings, persists vectors, tracks revision hashes, and incrementally reuses unchanged chunks.
- `query_interface.py` (`KnowledgeQueryService`) exposes a shared retrieval interface used by both debating agents.
