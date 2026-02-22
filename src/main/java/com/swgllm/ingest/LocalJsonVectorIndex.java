package com.swgllm.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LocalJsonVectorIndex implements VectorIndex {
    private final Map<String, IndexedChunk> chunks = new HashMap<>();
    private final Map<Integer, List<String>> annBuckets = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void upsert(DocumentChunk chunk, float[] embedding, String embeddingVersion) {
        IndexedChunk indexedChunk = new IndexedChunk(chunk, embedding, embeddingVersion, signature(embedding));
        chunks.put(chunk.id(), indexedChunk);
        rebuildAnnBuckets();
    }

    @Override
    public void removeBySourcePath(String sourcePath) {
        List<String> toRemove = chunks.values().stream()
                .filter(indexed -> indexed.chunk().metadata().sourcePath().equals(sourcePath))
                .map(indexed -> indexed.chunk().id())
                .toList();
        toRemove.forEach(chunks::remove);
        if (!toRemove.isEmpty()) {
            rebuildAnnBuckets();
        }
    }

    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK) {
        Set<String> candidates = candidateIds(queryEmbedding, topK);
        List<SearchResult> firstPass = candidates.stream()
                .map(chunks::get)
                .filter(item -> item != null)
                .map(indexed -> {
                    float cosine = cosine(queryEmbedding, indexed.embedding());
                    return new SearchResult(indexed.chunk(), cosine, cosine, "");
                })
                .sorted(Comparator.comparing(SearchResult::score).reversed())
                .limit(Math.max(topK * 3, topK))
                .toList();

        return firstPass.stream()
                .map(result -> {
                    float symbolBoost = symbolRerankScore(result.chunk(), queryEmbedding);
                    float rerank = (result.score() * 0.88f) + (symbolBoost * 0.12f);
                    return new SearchResult(result.chunk(), result.score(), rerank, result.citationSnippet());
                })
                .sorted(Comparator.comparing(SearchResult::rerankScore).reversed())
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
            int sig = entry.signature() == 0 ? signature(entry.embedding()) : entry.signature();
            index.chunks.put(entry.chunk().id(), new IndexedChunk(entry.chunk(), entry.embedding(), entry.embeddingVersion(), sig));
        }
        index.rebuildAnnBuckets();
        return index;
    }

    @Override
    public Map<String, List<String>> chunkIdsBySource() {
        return chunks.values().stream().collect(Collectors.groupingBy(
                item -> item.chunk().metadata().sourcePath(),
                Collectors.mapping(item -> item.chunk().id(), Collectors.toList())));
    }

    public boolean requiresReembedding(String embeddingVersion) {
        if (chunks.isEmpty()) {
            return false;
        }
        return chunks.values().stream().anyMatch(chunk -> !embeddingVersion.equals(chunk.embeddingVersion()));
    }

    public void clear() {
        chunks.clear();
        annBuckets.clear();
    }

    private Set<String> candidateIds(float[] queryEmbedding, int topK) {
        if (chunks.size() <= Math.max(150, topK * 20)) {
            return new HashSet<>(chunks.keySet());
        }
        Set<String> candidates = new HashSet<>();
        int querySignature = signature(queryEmbedding);
        List<Integer> probes = List.of(querySignature, querySignature ^ 0x00FF, querySignature ^ 0xFF00, querySignature ^ 0x0F0F);
        for (Integer probe : probes) {
            List<String> ids = annBuckets.getOrDefault(probe, List.of());
            candidates.addAll(ids);
        }
        if (candidates.size() < topK * 5) {
            candidates.addAll(chunks.keySet());
        }
        return candidates;
    }

    private void rebuildAnnBuckets() {
        annBuckets.clear();
        for (IndexedChunk chunk : chunks.values()) {
            annBuckets.computeIfAbsent(chunk.signature(), unused -> new ArrayList<>()).add(chunk.chunk().id());
        }
    }

    private float symbolRerankScore(DocumentChunk chunk, float[] queryEmbedding) {
        if (chunk.metadata().symbols().isEmpty()) {
            return 0f;
        }
        float sum = 0f;
        for (String symbol : chunk.metadata().symbols()) {
            sum += cosine(queryEmbedding, symbolEmbedding(symbol));
        }
        return sum / chunk.metadata().symbols().size();
    }

    private float[] symbolEmbedding(String symbol) {
        float[] vector = new float[64];
        String[] tokens = symbol.toLowerCase().split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), vector.length);
            vector[index] += 1f;
        }
        normalize(vector);
        return vector;
    }

    private static int signature(float[] embedding) {
        int signature = 0;
        for (int i = 0; i < Math.min(16, embedding.length); i++) {
            if (embedding[i] >= 0f) {
                signature |= (1 << i);
            }
        }
        return signature;
    }

    private static float cosine(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        float dot = 0f;
        float aNorm = 0f;
        float bNorm = 0f;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            aNorm += a[i] * a[i];
            bNorm += b[i] * b[i];
        }
        if (aNorm == 0f || bNorm == 0f) {
            return 0f;
        }
        return (float) (dot / Math.sqrt(aNorm * bNorm));
    }

    private static void normalize(float[] vector) {
        float norm = 0f;
        for (float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        if (norm == 0f) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    public record IndexedChunk(DocumentChunk chunk, float[] embedding, String embeddingVersion, int signature) {
    }
}
