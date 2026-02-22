package com.swgllm.versioning;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionRolloutManagerTest {

    @Test
    void shouldCanaryPromoteAndRollback() throws Exception {
        Path registry = Files.createTempFile("version", ".json");
        VersionRolloutManager manager = new VersionRolloutManager();

        ArtifactVersions candidate = new ArtifactVersions(
                SemanticVersion.parse("1.0.0"),
                SemanticVersion.parse("1.0.0"),
                SemanticVersion.parse("1.0.0"));

        manager.canaryRollout(registry, candidate);
        RolloutState promoted = manager.promoteCanary(registry);
        assertEquals("1.0.0", promoted.currentStable().modelVersion().toString());

        RolloutState rolledBack = manager.rollback(registry);
        assertEquals("0.1.0", rolledBack.currentStable().modelVersion().toString());
    }
}
