package com.swgllm.ingest;

public record SearchResult(DocumentChunk chunk, float score, float rerankScore, String citationSnippet) {
}
