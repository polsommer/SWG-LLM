package com.swgllm.runtime;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
    private RuntimeConfig runtime = new RuntimeConfig();

    public RuntimeConfig getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeConfig runtime) {
        this.runtime = runtime;
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
}
