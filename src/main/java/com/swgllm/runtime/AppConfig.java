package com.swgllm.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
    private RuntimeConfig runtime = new RuntimeConfig();
    private ContinuousModeConfig continuous = new ContinuousModeConfig();
    private AutoPublishConfig autopublish = new AutoPublishConfig();

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

    public AutoPublishConfig getAutopublish() {
        return autopublish;
    }

    public void setAutopublish(AutoPublishConfig autopublish) {
        this.autopublish = autopublish == null ? new AutoPublishConfig() : autopublish;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuntimeConfig {
        private int timeoutMs = 30000;
        private String defaultProfile = "cpu-low-memory";
        private int defaultRetrievalChunks = 4;
        private int defaultContextWindowTokens = 2048;
        private double defaultTemperature = 0.0;
        private double defaultTopP = 1.0;
        private int defaultMaxTokens = 256;
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

        public double getDefaultTemperature() {
            return defaultTemperature;
        }

        public void setDefaultTemperature(double defaultTemperature) {
            this.defaultTemperature = defaultTemperature;
        }

        public double getDefaultTopP() {
            return defaultTopP;
        }

        public void setDefaultTopP(double defaultTopP) {
            this.defaultTopP = defaultTopP;
        }

        public int getDefaultMaxTokens() {
            return defaultMaxTokens;
        }

        public void setDefaultMaxTokens(int defaultMaxTokens) {
            this.defaultMaxTokens = defaultMaxTokens;
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
        private Double temperature;
        private Double topP;
        private Integer maxTokens;

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

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Double getTopP() {
            return topP;
        }

        public void setTopP(Double topP) {
            this.topP = topP;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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
        private long idleTimeoutMs = 0;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AutoPublishConfig {
        private boolean enabled = true;
        private String targetRepoUrl = "https://github.com/polsommer/llm-dsrc.git";
        private String workspacePath = ".swgllm/autopublish/workspace";
        private List<String> allowedBranches = List.of("main");
        private double requiredMinScoreDelta = 0.0;
        private int maxPushesPerDay = 3;
        private boolean manualApprovalForHighImpactChanges = false;
        private boolean dryRun = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTargetRepoUrl() {
            return targetRepoUrl;
        }

        public void setTargetRepoUrl(String targetRepoUrl) {
            this.targetRepoUrl = targetRepoUrl;
        }

        public String getWorkspacePath() {
            return workspacePath;
        }

        public void setWorkspacePath(String workspacePath) {
            this.workspacePath = workspacePath;
        }

        public List<String> getAllowedBranches() {
            return allowedBranches;
        }

        public void setAllowedBranches(List<String> allowedBranches) {
            this.allowedBranches = allowedBranches == null ? List.of("main") : allowedBranches;
        }

        public double getRequiredMinScoreDelta() {
            return requiredMinScoreDelta;
        }

        public void setRequiredMinScoreDelta(double requiredMinScoreDelta) {
            this.requiredMinScoreDelta = requiredMinScoreDelta;
        }

        public int getMaxPushesPerDay() {
            return maxPushesPerDay;
        }

        public void setMaxPushesPerDay(int maxPushesPerDay) {
            this.maxPushesPerDay = maxPushesPerDay;
        }

        public boolean isManualApprovalForHighImpactChanges() {
            return manualApprovalForHighImpactChanges;
        }

        public void setManualApprovalForHighImpactChanges(boolean manualApprovalForHighImpactChanges) {
            this.manualApprovalForHighImpactChanges = manualApprovalForHighImpactChanges;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }
    }
}
