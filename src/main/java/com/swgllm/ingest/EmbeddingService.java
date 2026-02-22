package com.swgllm.ingest;

public interface EmbeddingService {
    float[] embed(String text);

    int dimension();

    default String version() {
        return "legacy-v1";
    }
}
