package com.swgllm.inference;

import java.util.List;

import com.swgllm.ingest.SearchResult;

public record InferenceContext(
        List<String> conversationHistory,
        List<SearchResult> retrievedSnippets) {
}
