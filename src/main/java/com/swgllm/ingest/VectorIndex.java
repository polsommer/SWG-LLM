package com.swgllm.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface VectorIndex {
    void upsert(DocumentChunk chunk, float[] embedding);

    void removeBySourcePath(String sourcePath);

    List<SearchResult> search(float[] queryEmbedding, int topK);

    void save(Path path) throws IOException;

    Map<String, List<String>> chunkIdsBySource();
}
