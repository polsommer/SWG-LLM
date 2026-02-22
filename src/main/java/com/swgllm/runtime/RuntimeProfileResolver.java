package com.swgllm.runtime;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeProfileResolver {
    private static final Logger log = LoggerFactory.getLogger(RuntimeProfileResolver.class);

    public ResolvedProfile resolve(AppConfig.RuntimeConfig runtimeConfig, String requestedProfile) {
        String profileName = requestedProfile == null || requestedProfile.isBlank()
                ? runtimeConfig.getDefaultProfile()
                : requestedProfile;
        Map<String, AppConfig.RuntimeProfile> profiles = runtimeConfig.getProfiles();
        AppConfig.RuntimeProfile profile = profiles.get(profileName);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown runtime profile: " + profileName);
        }

        String backend = normalizeBackend(profile.getBackend());
        if ("intel-igpu".equals(profileName) && !isIntelIgpuAvailable()) {
            log.warn("intel-igpu profile requested but iGPU acceleration unavailable; falling back to cpu-low-memory profile");
            profileName = "cpu-low-memory";
            profile = profiles.get(profileName);
            if (profile == null) {
                throw new IllegalArgumentException("Fallback profile cpu-low-memory is not configured");
            }
            backend = normalizeBackend(profile.getBackend());
        }

        int retrievalChunks = profile.getRetrievalChunks() > 0
                ? profile.getRetrievalChunks()
                : runtimeConfig.getDefaultRetrievalChunks();
        int contextWindowTokens = profile.getContextWindowTokens() > 0
                ? profile.getContextWindowTokens()
                : runtimeConfig.getDefaultContextWindowTokens();

        return new ResolvedProfile(
                profileName,
                profile.getModel(),
                backend,
                contextWindowTokens,
                retrievalChunks);
    }

    static boolean isIntelIgpuAvailable() {
        String property = System.getProperty("swgllm.intelIgpuAvailable", "false");
        if ("true".equalsIgnoreCase(property)) {
            return true;
        }
        String env = System.getenv("SWGLLM_INTEL_IGPU_AVAILABLE");
        return "1".equals(env) || "true".equalsIgnoreCase(env);
    }

    private static String normalizeBackend(String backend) {
        if (backend == null) {
            return "cpu";
        }
        String normalized = backend.toLowerCase(Locale.ROOT);
        if (normalized.contains("igpu") || normalized.contains("openvino") || normalized.contains("onednn")) {
            return "intel-igpu";
        }
        return "cpu";
    }

    public record ResolvedProfile(
            String profileName,
            String model,
            String backend,
            int contextWindowTokens,
            int retrievalChunks) {
    }
}
