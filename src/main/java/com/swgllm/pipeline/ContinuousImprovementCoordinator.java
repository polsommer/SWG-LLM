package com.swgllm.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swgllm.governance.GovernanceMetrics;
import com.swgllm.governance.GovernancePolicy;
import com.swgllm.governance.GovernanceTestResult;
import com.swgllm.ingest.GitRepositoryManager;
import com.swgllm.ingest.IngestionReport;
import com.swgllm.ingest.IngestionService;
import com.swgllm.runtime.AppConfig;
import com.swgllm.versioning.ArtifactVersions;
import com.swgllm.versioning.RolloutState;

public class ContinuousImprovementCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ContinuousImprovementCoordinator.class);

    private final GitRepositoryManager gitRepositoryManager;
    private final IngestionService ingestionService;
    private final OfflineImprovementPipeline improvementPipeline;
    private final AppConfig.ContinuousModeConfig continuousConfig;
    private final Path repoPath;
    private final String repoUrl;
    private final Path repoCacheDir;
    private final Path indexPath;
    private final Path ingestStatePath;
    private final Path feedbackPath;
    private final Path datasetPath;
    private final Path adapterDir;
    private final Path versionRegistryPath;
    private final ArtifactVersions candidate;
    private final GovernanceMetrics metrics;
    private final GovernancePolicy policy;
    private final Path evalArtifactPath;
    private final Path coordinatorStatePath;

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final ObjectMapper mapper = new ObjectMapper();

    public ContinuousImprovementCoordinator(
            GitRepositoryManager gitRepositoryManager,
            IngestionService ingestionService,
            OfflineImprovementPipeline improvementPipeline,
            AppConfig.ContinuousModeConfig continuousConfig,
            Path repoPath,
            String repoUrl,
            Path repoCacheDir,
            Path indexPath,
            Path ingestStatePath,
            Path feedbackPath,
            Path datasetPath,
            Path adapterDir,
            Path versionRegistryPath,
            ArtifactVersions candidate,
            GovernanceMetrics metrics,
            GovernancePolicy policy,
            Path evalArtifactPath) {
        this.gitRepositoryManager = gitRepositoryManager;
        this.ingestionService = ingestionService;
        this.improvementPipeline = improvementPipeline;
        this.continuousConfig = continuousConfig;
        this.repoPath = repoPath;
        this.repoUrl = repoUrl;
        this.repoCacheDir = repoCacheDir;
        this.indexPath = indexPath;
        this.ingestStatePath = ingestStatePath;
        this.feedbackPath = feedbackPath;
        this.datasetPath = datasetPath;
        this.adapterDir = adapterDir;
        this.versionRegistryPath = versionRegistryPath;
        this.candidate = candidate;
        this.metrics = metrics;
        this.policy = policy;
        this.evalArtifactPath = evalArtifactPath;
        this.coordinatorStatePath = Path.of(continuousConfig.getStatePath());
    }

    public void requestStop() {
        stopRequested.set(true);
    }

    public void runLoop() throws IOException, InterruptedException {
        validateConfig();
        ContinuousState state = loadState();
        long processStart = System.currentTimeMillis();
        if (state.startedAtEpochMs <= 0) {
            state.startedAtEpochMs = processStart;
        }
        if (state.lastActivityAtEpochMs <= 0) {
            state.lastActivityAtEpochMs = processStart;
        }
        persistState(state);

        while (!stopRequested.get()) {
            long now = System.currentTimeMillis();
            if (shouldStop(state, processStart, now)) {
                break;
            }

            state.totalCycles++;
            state.lastCycleStartedAtEpochMs = now;
            state.lastCycleStatus = "running";
            state.lastUpdatedAtEpochMs = now;
            persistState(state);

            boolean didWork = false;
            boolean cycleHalted = false;
            try {
                if (isDue(state.lastIngestAtEpochMs, continuousConfig.getIngestIntervalMs(), now)) {
                    IngestionReport report = runWithRetries(this::runIngestion);
                    state.lastIngestAtEpochMs = System.currentTimeMillis();
                    state.lastIngestionCommit = report.commitHash();
                    state.lastIngestionTag = report.versionTag();
                    state.lastIngestionProcessedFiles = report.processedFiles();
                    state.lastIngestionSkippedFiles = report.skippedFiles();
                    state.lastIngestionTotalFiles = report.totalFiles();
                    state.lastSuccessfulCheckpoint = report.commitHash() + "@" + report.versionTag();
                    didWork = true;
                }

                if (isDue(state.lastImproveAtEpochMs, continuousConfig.getImproveIntervalMs(), now)) {
                    OfflineImprovementPipeline.PipelineResult result = runWithRetries(this::runImprove);
                    state.lastImproveAtEpochMs = System.currentTimeMillis();
                    state.lastImprovementExamples = result.trainingExamples();

                    String checkpoint = resolveCheckpoint(result);
                    if (checkpoint != null && !checkpoint.isBlank()) {
                        state.lastSuccessfulCheckpoint = checkpoint;
                    }

                    if (result.safetyDecision() != null && result.safetyDecision().halted()) {
                        cycleHalted = true;
                        state.lastCycleStatus = "halted";
                        state.lastError = result.safetyDecision().reason();
                    }
                    didWork = true;
                }

                if (cycleHalted) {
                    state.lastActivityAtEpochMs = System.currentTimeMillis();
                } else if (didWork) {
                    state.successfulCycles++;
                    state.lastActivityAtEpochMs = System.currentTimeMillis();
                    state.lastCycleStatus = "success";
                    state.lastError = null;
                } else {
                    state.lastCycleStatus = "idle";
                }
            } catch (Exception e) {
                state.failedCycles++;
                state.lastCycleStatus = "failed";
                state.lastError = e.getMessage();
                log.error("continuous.cycle.failed cycle={} reason={}", state.totalCycles, e.getMessage(), e);
            }

            state.lastCycleCompletedAtEpochMs = System.currentTimeMillis();
            state.lastUpdatedAtEpochMs = state.lastCycleCompletedAtEpochMs;
            persistState(state);

            long sleepMs = computeSleepMillis(state, state.lastCycleCompletedAtEpochMs);
            if (sleepMs > 0) {
                Thread.sleep(sleepMs);
            }
        }

        if (stopRequested.get()) {
            state.lastCycleStatus = "stopped";
        } else if (state.lastCycleStatus == null || "running".equals(state.lastCycleStatus)) {
            state.lastCycleStatus = "completed";
        }
        state.lastUpdatedAtEpochMs = System.currentTimeMillis();
        persistState(state);
    }

    private String resolveCheckpoint(OfflineImprovementPipeline.PipelineResult result) {
        if (result.adapterArtifact() != null && result.adapterArtifact().adapterId() != null) {
            return result.adapterArtifact().adapterId();
        }
        return rolloutCheckpoint(result.rolloutState());
    }

    private String rolloutCheckpoint(RolloutState rolloutState) {
        if (rolloutState == null || rolloutState.currentStable() == null) {
            return null;
        }
        ArtifactVersions stable = rolloutState.currentStable();
        return "stable:prompt=" + stable.promptTemplateVersion()
                + ",retriever=" + stable.retrieverVersion()
                + ",model=" + stable.modelVersion();
    }

    private void validateConfig() {
        if (continuousConfig.getIngestIntervalMs() < 0 || continuousConfig.getImproveIntervalMs() < 0) {
            throw new IllegalArgumentException("continuous intervals must be >= 0");
        }
        if (continuousConfig.getMaxRetries() < 0 || continuousConfig.getRetryBackoffMs() < 0) {
            throw new IllegalArgumentException("continuous retry settings must be >= 0");
        }
    }

    private boolean shouldStop(ContinuousState state, long processStart, long now) {
        if (continuousConfig.getMaxCycles() > 0 && state.totalCycles >= continuousConfig.getMaxCycles()) {
            log.info("continuous.stop reason=max-cycles cycles={}", state.totalCycles);
            return true;
        }
        if (continuousConfig.getMaxRuntimeMs() > 0 && (now - processStart) >= continuousConfig.getMaxRuntimeMs()) {
            log.info("continuous.stop reason=max-runtime runtimeMs={}", now - processStart);
            return true;
        }
        if (continuousConfig.getIdleTimeoutMs() > 0 && (now - state.lastActivityAtEpochMs) >= continuousConfig.getIdleTimeoutMs()) {
            log.info("continuous.stop reason=idle-timeout idleMs={}", now - state.lastActivityAtEpochMs);
            return true;
        }
        return false;
    }

    private boolean isDue(long lastRunAtEpochMs, long intervalMs, long now) {
        if (lastRunAtEpochMs <= 0) {
            return true;
        }
        return now - lastRunAtEpochMs >= intervalMs;
    }

    private long computeSleepMillis(ContinuousState state, long now) {
        long ingestSleep = remaining(state.lastIngestAtEpochMs, continuousConfig.getIngestIntervalMs(), now);
        long improveSleep = remaining(state.lastImproveAtEpochMs, continuousConfig.getImproveIntervalMs(), now);
        long nextDue = Math.min(ingestSleep, improveSleep);
        long bounded = Math.max(200L, Math.min(nextDue, 1000L));
        return nextDue == Long.MAX_VALUE ? 1000L : bounded;
    }

    private long remaining(long lastRunAtEpochMs, long intervalMs, long now) {
        if (lastRunAtEpochMs <= 0 || intervalMs == 0) {
            return 0;
        }
        long elapsed = now - lastRunAtEpochMs;
        return elapsed >= intervalMs ? 0 : intervalMs - elapsed;
    }

    private IngestionReport runIngestion() throws IOException, InterruptedException {
        Path resolvedRepo = resolveRepository();
        return ingestionService.ingest(resolvedRepo, indexPath, ingestStatePath);
    }

    private OfflineImprovementPipeline.PipelineResult runImprove() throws IOException {
        return improvementPipeline.run(feedbackPath, datasetPath, adapterDir, versionRegistryPath, candidate, metrics, List.<GovernanceTestResult>of(), evalArtifactPath, policy);
    }

    private Path resolveRepository() throws IOException, InterruptedException {
        if (repoUrl != null && !repoUrl.isBlank()) {
            return gitRepositoryManager.prepareRepository(repoUrl, repoCacheDir);
        }
        if (!Files.isDirectory(repoPath)) {
            throw new IllegalArgumentException("Invalid --repo-path: " + repoPath.toAbsolutePath().normalize());
        }
        return repoPath;
    }

    private <T> T runWithRetries(ThrowingSupplier<T> supplier) throws Exception {
        Exception last = null;
        int maxAttempts = Math.max(1, continuousConfig.getMaxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (Exception e) {
                last = e;
                if (attempt == maxAttempts) {
                    break;
                }
                long backoff = continuousConfig.getRetryBackoffMs() * attempt;
                log.warn("continuous.retry attempt={} maxAttempts={} backoffMs={} reason={}", attempt, maxAttempts, backoff, e.getMessage());
                Thread.sleep(backoff);
            }
        }
        throw last;
    }

    private ContinuousState loadState() throws IOException {
        if (!Files.exists(coordinatorStatePath)) {
            return new ContinuousState();
        }
        return mapper.readValue(coordinatorStatePath.toFile(), ContinuousState.class);
    }

    private void persistState(ContinuousState state) throws IOException {
        Path parent = coordinatorStatePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(coordinatorStatePath.toFile(), state);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public static class ContinuousState {
        public long startedAtEpochMs;
        public long totalCycles;
        public long successfulCycles;
        public long failedCycles;
        public long lastCycleStartedAtEpochMs;
        public long lastCycleCompletedAtEpochMs;
        public String lastCycleStatus;
        public String lastError;
        public long lastIngestAtEpochMs;
        public long lastImproveAtEpochMs;
        public long lastActivityAtEpochMs;
        public long lastUpdatedAtEpochMs;
        public String lastIngestionCommit;
        public String lastIngestionTag;
        public int lastIngestionProcessedFiles;
        public int lastIngestionSkippedFiles;
        public int lastIngestionTotalFiles;
        public int lastImprovementExamples;
        public String lastSuccessfulCheckpoint;
    }
}
