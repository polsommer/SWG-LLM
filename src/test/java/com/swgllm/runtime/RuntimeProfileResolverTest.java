package com.swgllm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RuntimeProfileResolverTest {

    @Test
    void shouldResolveCpuProfile() {
        AppConfig.RuntimeConfig config = new AppConfig.RuntimeConfig();

        AppConfig.RuntimeProfile cpu = new AppConfig.RuntimeProfile();
        cpu.setModel("phi-q4");
        cpu.setBackend("cpu");
        cpu.setContextWindowTokens(2048);
        cpu.setRetrievalChunks(4);

        config.setProfiles(Map.of("cpu-low-memory", cpu));

        RuntimeProfileResolver.ResolvedProfile resolved = new RuntimeProfileResolver().resolve(config, "cpu-low-memory");

        assertEquals("cpu-low-memory", resolved.profileName());
        assertEquals("cpu", resolved.backend());
        assertEquals(4, resolved.retrievalChunks());
    }

    @Test
    void shouldFailForUnknownProfile() {
        AppConfig.RuntimeConfig config = new AppConfig.RuntimeConfig();
        config.setProfiles(Map.of());

        assertThrows(IllegalArgumentException.class, () -> new RuntimeProfileResolver().resolve(config, "missing"));
    }
}
