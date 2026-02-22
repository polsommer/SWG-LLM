package com.swgllm.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class RetrievalService {
    private final EmbeddingService embeddingService;

    public RetrievalService(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public List<SearchResult> retrieve(Path indexPath, String query, int topK) throws IOException {
        LocalJsonVectorIndex index = LocalJsonVectorIndex.load(indexPath);
        float[] queryEmbedding = embeddingService.embed(query);

        List<SearchResult> semantic = index.search(queryEmbedding, Math.max(topK * 3, topK));

        Set<String> queryTerms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());

        return semantic.stream()
                .map(result -> {
                    float lexical = lexicalScore(queryTerms, result.chunk().text());
                    float rerank = (result.score() * 0.7f) + (lexical * 0.3f);
                    return new SearchResult(result.chunk(), result.score(), rerank, citationSnippet(result.chunk()));
                })
                .sorted(Comparator.comparing(SearchResult::rerankScore).reversed())
                .limit(topK)
                .toList();
    }

    private float lexicalScore(Set<String> queryTerms, String text) {
        if (queryTerms.isEmpty() || text.isBlank()) {
            return 0f;
        }
        Set<String> words = Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
        long matches = queryTerms.stream().filter(words::contains).count();
        return (float) matches / queryTerms.size();
    }

    private String citationSnippet(DocumentChunk chunk) {
        String trimmed = chunk.text().strip();
        if (trimmed.length() > 240) {
            trimmed = trimmed.substring(0, 240) + "...";
        }
        return "%s:%d-%d %s".formatted(
                chunk.metadata().sourcePath(),
                chunk.metadata().startLine(),
                chunk.metadata().endLine(),
                trimmed.replaceAll("\\s+", " "));
    }
}
