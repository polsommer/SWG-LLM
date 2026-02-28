package com.swgllm.runtime;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
    private RuntimeConfig runtime = new RuntimeConfig();
    private ContinuousModeConfig continuous = new ContinuousModeConfig();

    public RuntimeConfig getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeConfig runtime) {
        this.runtime = runtime == null ? new RuntimeConfig() : runtime;
    }

    public ContinuousModeConfig getContinuous() {
        return continuous;
    }

    public void setContinuous(ContinuousModeConfig continuous) {
        this.continuous = continuous == null ? new ContinuousModeConfig() : continuous;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuntimeConfig {
        private int timeoutMs = 30000;
        private String defaultProfile = "cpu-low-memory";
        private int defaultRetrievalChunks = 4;
        private int defaultContextWindowTokens = 2048;
        private Map<String, RuntimeProfile> profiles = new HashMap<>();

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public String getDefaultProfile() {
            return defaultProfile;
        }

        public void setDefaultProfile(String defaultProfile) {
            this.defaultProfile = defaultProfile;
        }

        public int getDefaultRetrievalChunks() {
            return defaultRetrievalChunks;
        }

        public void setDefaultRetrievalChunks(int defaultRetrievalChunks) {
            this.defaultRetrievalChunks = defaultRetrievalChunks;
        }

        public int getDefaultContextWindowTokens() {
            return defaultContextWindowTokens;
        }

        public void setDefaultContextWindowTokens(int defaultContextWindowTokens) {
            this.defaultContextWindowTokens = defaultContextWindowTokens;
        }

        public Map<String, RuntimeProfile> getProfiles() {
            return profiles;
        }

        public void setProfiles(Map<String, RuntimeProfile> profiles) {
            this.profiles = profiles;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuntimeProfile {
        private String model;
        private String backend;
        private int contextWindowTokens;
        private int retrievalChunks;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public int getContextWindowTokens() {
            return contextWindowTokens;
        }

        public void setContextWindowTokens(int contextWindowTokens) {
            this.contextWindowTokens = contextWindowTokens;
        }

        public int getRetrievalChunks() {
            return retrievalChunks;
        }

        public void setRetrievalChunks(int retrievalChunks) {
            this.retrievalChunks = retrievalChunks;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContinuousModeConfig {
        private long ingestIntervalMs = 120000;
        private long improveIntervalMs = 300000;
        private int maxRetries = 3;
        private long retryBackoffMs = 5000;
        private long maxCycles = 0;
        private long maxRuntimeMs = 0;
        private long idleTimeoutMs = 900000;
        private String statePath = ".swgllm/continuous-state.json";

        public long getIngestIntervalMs() {
            return ingestIntervalMs;
        }

        public void setIngestIntervalMs(long ingestIntervalMs) {
            this.ingestIntervalMs = ingestIntervalMs;
        }

        public long getImproveIntervalMs() {
            return improveIntervalMs;
        }

        public void setImproveIntervalMs(long improveIntervalMs) {
            this.improveIntervalMs = improveIntervalMs;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public long getMaxCycles() {
            return maxCycles;
        }

        public void setMaxCycles(long maxCycles) {
            this.maxCycles = maxCycles;
        }

        public long getMaxRuntimeMs() {
            return maxRuntimeMs;
        }

        public void setMaxRuntimeMs(long maxRuntimeMs) {
            this.maxRuntimeMs = maxRuntimeMs;
        }

        public long getIdleTimeoutMs() {
            return idleTimeoutMs;
        }

        public void setIdleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
        }

        public String getStatePath() {
            return statePath;
        }

        public void setStatePath(String statePath) {
            this.statePath = statePath;
        }
    }
}
