package com.swgllm.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class IngestionStateStore {
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, String> load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new HashMap<>();
        }
        return mapper.readValue(path.toFile(), new TypeReference<Map<String, String>>() {
        });
    }

    public void save(Path path, Map<String, String> state) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), state);
    }

    public static String fingerprint(Path path) throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(path);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
