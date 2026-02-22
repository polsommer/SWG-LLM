package com.swgllm.versioning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class VersionRolloutManager {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    public RolloutState load(Path registryPath) throws IOException {
        if (!Files.exists(registryPath) || Files.size(registryPath) == 0L) {
            RolloutState initial = RolloutState.initial();
            save(registryPath, initial);
            return initial;
        }
        return objectMapper.readValue(registryPath.toFile(), RolloutState.class);
    }

    public RolloutState canaryRollout(Path registryPath, ArtifactVersions candidate) throws IOException {
        RolloutState current = load(registryPath);
        RolloutState updated = new RolloutState(current.currentStable(), current.currentStable(), candidate);
        save(registryPath, updated);
        return updated;
    }

    public RolloutState promoteCanary(Path registryPath) throws IOException {
        RolloutState current = load(registryPath);
        ArtifactVersions canary = current.currentCanary();
        RolloutState promoted = new RolloutState(current.currentStable(), canary, canary);
        save(registryPath, promoted);
        return promoted;
    }

    public RolloutState rollback(Path registryPath) throws IOException {
        RolloutState current = load(registryPath);
        RolloutState rolledBack = new RolloutState(current.previousStable(), current.previousStable(), current.previousStable());
        save(registryPath, rolledBack);
        return rolledBack;
    }

    private void save(Path registryPath, RolloutState state) throws IOException {
        if (registryPath.getParent() != null) {
            Files.createDirectories(registryPath.getParent());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(registryPath.toFile(), state);
    }
}
