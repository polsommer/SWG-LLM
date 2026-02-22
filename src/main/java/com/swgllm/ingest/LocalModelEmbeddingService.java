package com.swgllm.ingest;

import java.util.Locale;

public class LocalModelEmbeddingService implements EmbeddingService {
    private static final String VERSION = "local-swg-v2";
    private final int dimension;

    public LocalModelEmbeddingService(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split("\\W+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            addHashed(vector, "tok:" + token, 1.0f);
            if (token.length() >= 3) {
                for (int i = 0; i <= token.length() - 3; i++) {
                    addHashed(vector, "tri:" + token.substring(i, i + 3), 0.35f);
                }
            }
            if (isSwgDomainTerm(token)) {
                addHashed(vector, "domain:" + token, 1.6f);
            }
        }

        normalize(vector);
        return vector;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String version() {
        return VERSION;
    }

    private void addHashed(float[] vector, String key, float weight) {
        int index = Math.floorMod(key.hashCode(), vector.length);
        vector[index] += weight;
    }

    private static boolean isSwgDomainTerm(String token) {
        return token.contains("swg")
                || token.contains("tatooine")
                || token.contains("corellia")
                || token.contains("jedi")
                || token.contains("objecttemplate")
                || token.contains("datatables")
                || token.contains("server");
    }

    private static void normalize(float[] vector) {
        float norm = 0f;
        for (float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        if (norm <= 0f) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
