package com.swgllm.ingest;

import okhttp3.OkHttpClient;

public final class EmbeddingServices {
    private EmbeddingServices() {
    }

    public static EmbeddingService fromEnvironment(OkHttpClient httpClient) {
        EmbeddingService local = new LocalModelEmbeddingService(384);
        String endpoint = System.getenv("SWGLLM_EMBEDDING_URL");
        if (endpoint == null || endpoint.isBlank()) {
            return local;
        }
        String provider = System.getenv().getOrDefault("SWGLLM_EMBEDDING_PROVIDER", "custom");
        String apiKey = System.getenv("SWGLLM_EMBEDDING_API_KEY");
        return new ExternalProviderEmbeddingService(httpClient, endpoint, provider, apiKey, local);
    }
}
