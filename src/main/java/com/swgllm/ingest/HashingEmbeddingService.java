package com.swgllm.ingest;

import java.util.Locale;

public class HashingEmbeddingService implements EmbeddingService {
    private final int dimension;

    public HashingEmbeddingService(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String[] tokens = text.toLowerCase(Locale.ROOT).split("\\W+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int index = Math.floorMod(token.hashCode(), dimension);
            vector[index] += 1f;
        }

        float norm = 0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0f) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    @Override
    public int dimension() {
        return dimension;
    }
}
