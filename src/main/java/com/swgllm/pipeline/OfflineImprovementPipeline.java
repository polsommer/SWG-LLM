package com.swgllm.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.swgllm.feedback.FeedbackCaptureService;
import com.swgllm.governance.GovernanceEvaluation;
import com.swgllm.governance.GovernanceMetrics;
import com.swgllm.governance.GovernancePolicy;
import com.swgllm.governance.QualitySafetyRegressionSuite;
import com.swgllm.versioning.ArtifactVersions;
import com.swgllm.versioning.RolloutState;
import com.swgllm.versioning.VersionRolloutManager;

public class OfflineImprovementPipeline {
    private final OfflineDatasetBuilder datasetBuilder;
    private final AdapterUpdateJob adapterUpdateJob;
    private final QualitySafetyRegressionSuite regressionSuite;
    private final VersionRolloutManager rolloutManager;

    public OfflineImprovementPipeline() {
        FeedbackCaptureService feedbackCaptureService = new FeedbackCaptureService();
        this.datasetBuilder = new OfflineDatasetBuilder(feedbackCaptureService);
        this.adapterUpdateJob = new AdapterUpdateJob();
        this.regressionSuite = new QualitySafetyRegressionSuite();
        this.rolloutManager = new VersionRolloutManager();
    }

    public PipelineResult run(
            Path feedbackPath,
            Path datasetOutputPath,
            Path adapterDir,
            Path versionRegistryPath,
            ArtifactVersions candidate,
            GovernanceMetrics metrics,
            GovernancePolicy policy) throws IOException {
        List<TrainingExample> dataset = datasetBuilder.buildFromApprovedFeedback(feedbackPath, datasetOutputPath);
        Path adapterArtifact = adapterUpdateJob.runPeriodicUpdate(dataset, adapterDir);

        RolloutState canaryState = rolloutManager.canaryRollout(versionRegistryPath, candidate);
        GovernanceEvaluation evaluation = regressionSuite.evaluate(metrics, policy);
        if (!evaluation.passed()) {
            RolloutState rollbackState = rolloutManager.rollback(versionRegistryPath);
            return new PipelineResult(dataset.size(), adapterArtifact, evaluation, rollbackState);
        }
        RolloutState promoted = rolloutManager.promoteCanary(versionRegistryPath);
        return new PipelineResult(dataset.size(), adapterArtifact, evaluation, promoted);
    }

    public record PipelineResult(
            int trainingExamples,
            Path adapterArtifact,
            GovernanceEvaluation governanceEvaluation,
            RolloutState rolloutState) {
    }
}
