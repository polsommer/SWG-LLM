package com.swgllm.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LocalJsonVectorIndex implements VectorIndex {
    private final Map<String, IndexedChunk> chunks = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void upsert(DocumentChunk chunk, float[] embedding) {
        chunks.put(chunk.id(), new IndexedChunk(chunk, embedding));
    }

    @Override
    public void removeBySourcePath(String sourcePath) {
        List<String> toRemove = chunks.values().stream()
                .filter(indexed -> indexed.chunk().metadata().sourcePath().equals(sourcePath))
                .map(indexed -> indexed.chunk().id())
                .toList();
        toRemove.forEach(chunks::remove);
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK) {
        return chunks.values().stream()
                .map(indexed -> {
                    float score = dot(queryEmbedding, indexed.embedding());
                    return new SearchResult(indexed.chunk(), score, score, "");
                })
                .sorted(Comparator.comparing(SearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public void save(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), chunks.values());
    }

    public static LocalJsonVectorIndex load(Path path) throws IOException {
        LocalJsonVectorIndex index = new LocalJsonVectorIndex();
        if (!Files.exists(path)) {
            return index;
        }
        ObjectMapper mapper = new ObjectMapper();
        List<IndexedChunk> loaded = mapper.readValue(path.toFile(), new TypeReference<List<IndexedChunk>>() {
        });
        for (IndexedChunk entry : loaded) {
            index.chunks.put(entry.chunk().id(), entry);
        }
        return index;
    }

    @Override
    public Map<String, List<String>> chunkIdsBySource() {
        return chunks.values().stream().collect(Collectors.groupingBy(
                item -> item.chunk().metadata().sourcePath(),
                Collectors.mapping(item -> item.chunk().id(), Collectors.toList())));
    }

    private static float dot(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        float sum = 0f;
        for (int i = 0; i < len; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public record IndexedChunk(DocumentChunk chunk, float[] embedding) {
    }
}
