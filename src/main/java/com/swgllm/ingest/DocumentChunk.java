package com.swgllm.ingest;

public record DocumentChunk(String id, String text, ChunkMetadata metadata) {
}
