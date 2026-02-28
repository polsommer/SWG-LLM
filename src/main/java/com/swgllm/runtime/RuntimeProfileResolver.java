package com.swgllm.runtime;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuntimeProfileResolver {
    private static final Logger log = LoggerFactory.getLogger(RuntimeProfileResolver.class);
    private final IntelGpuCapabilityProbe capabilityProbe;

    public RuntimeProfileResolver() {
        this(new IntelGpuCapabilityProbe());
    }

    public RuntimeProfileResolver(IntelGpuCapabilityProbe capabilityProbe) {
        this.capabilityProbe = capabilityProbe;
    }

    public ResolvedProfile resolve(AppConfig.RuntimeConfig runtimeConfig, String requestedProfile) {
        String profileName = requestedProfile == null || requestedProfile.isBlank()
                ? runtimeConfig.getDefaultProfile()
                : requestedProfile;
        String requested = profileName;

        Map<String, AppConfig.RuntimeProfile> profiles = runtimeConfig.getProfiles();
        AppConfig.RuntimeProfile profile = profiles.get(profileName);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown runtime profile: " + profileName);
        }

        String backend = normalizeBackend(profile.getBackend());
        String fallbackCause = "";

        if ("intel-igpu".equals(profileName)) {
            IntelGpuCapabilityProbe.CapabilityReport report = capabilityProbe.probeUbuntu2204();
            if (!report.available()) {
                fallbackCause = report.reason();
                log.warn("intel-igpu profile requested but iGPU acceleration unavailable: {}; falling back to cpu-low-memory profile", fallbackCause);
                profileName = "cpu-low-memory";
                profile = profiles.get(profileName);
                if (profile == null) {
                    throw new IllegalArgumentException("Fallback profile cpu-low-memory is not configured");
                }
                backend = normalizeBackend(profile.getBackend());
            }
        }

        int retrievalChunks = profile.getRetrievalChunks() > 0
                ? profile.getRetrievalChunks()
                : runtimeConfig.getDefaultRetrievalChunks();
        int contextWindowTokens = profile.getContextWindowTokens() > 0
                ? profile.getContextWindowTokens()
                : runtimeConfig.getDefaultContextWindowTokens();

        double temperature = profile.getTemperature() != null
                ? profile.getTemperature()
                : runtimeConfig.getDefaultTemperature();
        double topP = profile.getTopP() != null
                ? profile.getTopP()
                : runtimeConfig.getDefaultTopP();
        int maxTokens = profile.getMaxTokens() != null
                ? profile.getMaxTokens()
                : runtimeConfig.getDefaultMaxTokens();

        return new ResolvedProfile(
                requested,
                profileName,
                profile.getModel(),
                backend,
                contextWindowTokens,
                retrievalChunks,
                temperature,
                topP,
                maxTokens,
                fallbackCause);
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
            String requestedProfile,
            String profileName,
            String model,
            String backend,
            int contextWindowTokens,
            int retrievalChunks,
            double temperature,
            double topP,
            int maxTokens,
            String fallbackCause) {
    }
}
