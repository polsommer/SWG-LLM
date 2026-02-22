package com.swgllm.ingest;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ExternalProviderEmbeddingService implements EmbeddingService {
    private static final MediaType JSON = MediaType.parse("application/json");
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String endpoint;
    private final String provider;
    private final String apiKey;
    private final EmbeddingService fallback;

    public ExternalProviderEmbeddingService(OkHttpClient httpClient,
            String endpoint,
            String provider,
            String apiKey,
            EmbeddingService fallback) {
        this.httpClient = httpClient;
        this.mapper = new ObjectMapper();
        this.endpoint = endpoint;
        this.provider = provider;
        this.apiKey = apiKey;
        this.fallback = fallback;
    }

    @Override
    public float[] embed(String text) {
        try {
            String payload = mapper.writeValueAsString(Map.of("input", text));
            Request.Builder requestBuilder = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(payload, JSON));
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return fallback.embed(text);
                }
                JsonNode root = mapper.readTree(response.body().string());
                JsonNode vectorNode = root.path("embedding");
                if (!vectorNode.isArray()) {
                    return fallback.embed(text);
                }
                float[] out = new float[vectorNode.size()];
                for (int i = 0; i < vectorNode.size(); i++) {
                    out[i] = (float) vectorNode.get(i).asDouble();
                }
                return out;
            }
        } catch (IOException e) {
            return fallback.embed(text);
        }
    }

    @Override
    public int dimension() {
        return fallback.dimension();
    }

    @Override
    public String version() {
        return "external-" + provider + "-v1";
    }
}
