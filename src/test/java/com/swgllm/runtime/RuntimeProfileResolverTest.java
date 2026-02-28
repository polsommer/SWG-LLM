package com.swgllm.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RuntimeProfileResolverTest {

    @Test
    void requestedIntelIgpuShouldRemainActiveWhenCapabilityAvailable() {
        RuntimeProfileResolver resolver = new RuntimeProfileResolver(new IntelGpuCapabilityProbe(new StubInspector(
                "ID=ubuntu\nVERSION_ID=\"22.04\"\n",
                true,
                true,
                true,
                false)));

        RuntimeProfileResolver.ResolvedProfile resolved = resolver.resolve(config(), "intel-igpu");

        assertEquals("intel-igpu", resolved.requestedProfile());
        assertEquals("intel-igpu", resolved.profileName());
        assertEquals("intel-igpu", resolved.backend());
        assertEquals("", resolved.fallbackCause());
    }

    @Test
    void requestedIntelIgpuShouldFallbackToCpuWhenCapabilityUnavailable() {
        RuntimeProfileResolver resolver = new RuntimeProfileResolver(new IntelGpuCapabilityProbe(new StubInspector(
                "ID=ubuntu\nVERSION_ID=\"22.04\"\n",
                false,
                true,
                true,
                false)));

        RuntimeProfileResolver.ResolvedProfile resolved = resolver.resolve(config(), "intel-igpu");

        assertEquals("intel-igpu", resolved.requestedProfile());
        assertEquals("cpu-low-memory", resolved.profileName());
        assertEquals("cpu", resolved.backend());
        assertEquals("intelDriNode: Missing /dev/dri render node", resolved.fallbackCause());
    }

    @Test
    void shouldResolveCpuProfileWithoutChanges() {
        RuntimeProfileResolver resolver = new RuntimeProfileResolver(new IntelGpuCapabilityProbe(new StubInspector(
                "",
                false,
                false,
                false,
                false)));

        RuntimeProfileResolver.ResolvedProfile resolved = resolver.resolve(config(), "cpu-low-memory");

        assertEquals("cpu-low-memory", resolved.requestedProfile());
        assertEquals("cpu-low-memory", resolved.profileName());
        assertEquals("cpu", resolved.backend());
        assertEquals("", resolved.fallbackCause());
        assertEquals(4, resolved.retrievalChunks());
        assertEquals(0.0, resolved.temperature());
        assertEquals(1.0, resolved.topP());
        assertEquals(256, resolved.maxTokens());
    }

    @Test
    void shouldFailForUnknownProfile() {
        AppConfig.RuntimeConfig config = new AppConfig.RuntimeConfig();
        config.setProfiles(Map.of());

        assertThrows(IllegalArgumentException.class, () -> new RuntimeProfileResolver().resolve(config, "missing"));
    }

    private static AppConfig.RuntimeConfig config() {
        AppConfig.RuntimeConfig config = new AppConfig.RuntimeConfig();

        AppConfig.RuntimeProfile cpu = new AppConfig.RuntimeProfile();
        cpu.setModel("phi-q4");
        cpu.setBackend("cpu");
        cpu.setContextWindowTokens(2048);
        cpu.setRetrievalChunks(4);

        AppConfig.RuntimeProfile igpu = new AppConfig.RuntimeProfile();
        igpu.setModel("phi-q4");
        igpu.setBackend("openvino-onednn-igpu");
        igpu.setContextWindowTokens(3072);
        igpu.setRetrievalChunks(5);

        config.setDefaultTemperature(0.0);
        config.setDefaultTopP(1.0);
        config.setDefaultMaxTokens(256);

        cpu.setTemperature(0.0);
        cpu.setTopP(1.0);
        cpu.setMaxTokens(256);

        igpu.setTemperature(0.0);
        igpu.setTopP(1.0);
        igpu.setMaxTokens(256);

        config.setProfiles(Map.of("cpu-low-memory", cpu, "intel-igpu", igpu));
        return config;
    }

    private record StubInspector(
            String osRelease,
            boolean driExists,
            boolean runtimePresent,
            boolean mediaDriverPresent,
            boolean oneApiEnv) implements IntelGpuCapabilityProbe.SystemInspector {

        @Override
        public String readOsRelease() {
            return osRelease;
        }

        @Override
        public boolean fileExists(String path) {
            return switch (path) {
                case "/dev/dri/renderD128", "/dev/dri/card0" -> driExists;
                case "/usr/lib/x86_64-linux-gnu/dri/iHD_drv_video.so" -> mediaDriverPresent;
                default -> false;
            };
        }

        @Override
        public boolean commandExists(String command) {
            return switch (command) {
                case "sycl-ls", "clinfo" -> runtimePresent;
                case "vainfo" -> mediaDriverPresent;
                default -> false;
            };
        }

        @Override
        public boolean envEnabled(String key) {
            return oneApiEnv && "ONEAPI_ROOT".equals(key);
        }
    }
}
