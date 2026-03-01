package com.swgllm.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swgllm.governance.AutonomySafetyGovernor;
import com.swgllm.governance.GovernanceMetrics;
import com.swgllm.governance.GovernancePolicy;
import com.swgllm.governance.GovernanceTestResult;
import com.swgllm.ingest.EmbeddingService;
import com.swgllm.ingest.GitRepositoryManager;
import com.swgllm.ingest.IngestionService;
import com.swgllm.runtime.AppConfig;
import com.swgllm.versioning.ArtifactVersions;
import com.swgllm.versioning.RolloutState;
import com.swgllm.versioning.SemanticVersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContinuousImprovementCoordinatorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldHandleHaltedImproveResultWithNullAdapterArtifact() throws Exception {
        Path repoPath = Files.createDirectories(tempDir.resolve("repo"));
        Path statePath = tempDir.resolve("continuous-state.json");

        AppConfig.ContinuousModeConfig config = new AppConfig.ContinuousModeConfig();
        config.setIngestIntervalMs(0);
        config.setImproveIntervalMs(0);
        config.setMaxRetries(0);
        config.setRetryBackoffMs(0);
        config.setMaxCycles(1);
        config.setStatePath(statePath.toString());

        IngestionService ingestionService = new IngestionService(new FixedEmbeddingService());
        OfflineImprovementPipeline pipeline = new OfflineImprovementPipeline() {
            @Override
            public PipelineResult run(
                    Path feedbackPath,
                    Path datasetOutputPath,
                    Path adapterDir,
                    Path versionRegistryPath,
                    ArtifactVersions candidate,
                    GovernanceMetrics metrics,
                    List<GovernanceTestResult> testResults,
                    Path evaluationArtifactDir,
                    GovernancePolicy policy) {
                RolloutState rolloutState = new RolloutState(
                        candidate,
                        candidate,
                        candidate);
                return new PipelineResult(
                        5,
                        null,
                        null,
                        rolloutState,
                        evaluationArtifactDir,
                        AutonomySafetyGovernor.GovernorDecision.halt("intentional safety halt", "incident-123"));
            }
        };

        ContinuousImprovementCoordinator coordinator = new ContinuousImprovementCoordinator(
                new GitRepositoryManager(),
                ingestionService,
                pipeline,
                config,
                repoPath,
                null,
                tempDir.resolve("repo-cache"),
                tempDir.resolve("index.json"),
                tempDir.resolve("ingest-state.json"),
                tempDir.resolve("feedback.json"),
                tempDir.resolve("dataset.json"),
                tempDir.resolve("adapters"),
                tempDir.resolve("rollout.json"),
                new ArtifactVersions(new SemanticVersion(1, 2, 3), new SemanticVersion(2, 3, 4), new SemanticVersion(3, 4, 5)),
                new GovernanceMetrics(0.5, 0.5, 0.5),
                new GovernancePolicy(0.1, 0.1, 0.1),
                tempDir.resolve("eval-artifacts"));

        coordinator.runLoop();

        ContinuousImprovementCoordinator.ContinuousState state = new ObjectMapper()
                .readValue(statePath.toFile(), ContinuousImprovementCoordinator.ContinuousState.class);

        assertEquals(1, state.totalCycles);
        assertEquals("halted", state.lastCycleStatus);
        assertEquals("intentional safety halt", state.lastError);
        assertNotNull(state.lastSuccessfulCheckpoint);
        assertEquals("stable:prompt=1.2.3,retriever=2.3.4,model=3.4.5", state.lastSuccessfulCheckpoint);
    }

    private static class FixedEmbeddingService implements EmbeddingService {
        @Override
        public float[] embed(String text) {
            return new float[] { 0.1f };
        }

        @Override
        public int dimension() {
            return 1;
        }
    }
}
