package com.swgllm.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swgllm.feedback.FeedbackCaptureService;
import com.swgllm.governance.AutonomySafetyGovernor;
import com.swgllm.governance.GovernanceEvaluation;
import com.swgllm.governance.GovernanceMetrics;
import com.swgllm.governance.GovernancePolicy;
import com.swgllm.governance.GovernanceTestResult;
import com.swgllm.governance.QualitySafetyRegressionSuite;
import com.swgllm.versioning.ArtifactVersions;
import com.swgllm.versioning.RolloutState;
import com.swgllm.versioning.VersionRolloutManager;

public class OfflineImprovementPipeline {
    private final OfflineDatasetBuilder datasetBuilder;
    private final AdapterUpdateJob adapterUpdateJob;
    private final QualitySafetyRegressionSuite regressionSuite;
    private final VersionRolloutManager rolloutManager;
    private final AutonomySafetyGovernor safetyGovernor;
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    public OfflineImprovementPipeline() {
        this(new OfflineDatasetBuilder(new FeedbackCaptureService()), new AdapterUpdateJob(), new QualitySafetyRegressionSuite(), new VersionRolloutManager(), new AutonomySafetyGovernor());
    }

    OfflineImprovementPipeline(
            OfflineDatasetBuilder datasetBuilder,
            AdapterUpdateJob adapterUpdateJob,
            QualitySafetyRegressionSuite regressionSuite,
            VersionRolloutManager rolloutManager,
            AutonomySafetyGovernor safetyGovernor) {
        this.datasetBuilder = datasetBuilder;
        this.adapterUpdateJob = adapterUpdateJob;
        this.regressionSuite = regressionSuite;
        this.rolloutManager = rolloutManager;
        this.safetyGovernor = safetyGovernor;
    }

    public PipelineResult run(
            Path feedbackPath,
            Path datasetOutputPath,
            Path adapterDir,
            Path versionRegistryPath,
            ArtifactVersions candidate,
            GovernanceMetrics metrics,
            List<GovernanceTestResult> testResults,
            Path evaluationArtifactDir,
            GovernancePolicy policy) throws IOException {
        Path governorStatePath = adapterDir.resolve("governor-state.json");
        Path incidentPath = evaluationArtifactDir.resolve("governor-incidents.jsonl");

        List<TrainingExample> previousDataset = loadDatasetIfPresent(datasetOutputPath);
        List<TrainingExample> dataset = datasetBuilder.buildFromApprovedFeedback(feedbackPath, datasetOutputPath);

        int datasetDelta = Math.abs(dataset.size() - previousDataset.size());
        int promptDiffSize = calculatePromptDiffSize(previousDataset, dataset);
        double confidenceMargin = metrics.swgDomainAccuracy() - policy.minSwgDomainAccuracy();
        boolean uncertainImprovement = confidenceMargin < 0.01;

        AutonomySafetyGovernor.GovernorDecision safetyDecision = safetyGovernor.evaluateTrainingCycle(
                new AutonomySafetyGovernor.TrainingCycleContext(
                        governorStatePath,
                        incidentPath,
                        datasetDelta,
                        promptDiffSize,
                        confidenceMargin,
                        uncertainImprovement));

        if (safetyDecision.halted()) {
            RolloutState currentState = rolloutManager.load(versionRegistryPath);
            safetyGovernor.recordOutcome(governorStatePath, false, false);
            return new PipelineResult(dataset.size(), null, null, currentState, evaluationArtifactDir, safetyDecision);
        }

        AdapterArtifact adapterArtifact = adapterUpdateJob.runPeriodicUpdate(dataset, adapterDir);

        RolloutState canaryState = rolloutManager.canaryRollout(versionRegistryPath, candidate);
        GovernanceEvaluation evaluation = regressionSuite.evaluate(metrics, policy, testResults);
        if (!evaluation.passed()) {
            RolloutState rollbackState = rolloutManager.rollback(versionRegistryPath);
            safetyGovernor.recordOutcome(governorStatePath, false, true);
            return new PipelineResult(dataset.size(), adapterArtifact, evaluation, rollbackState, evaluationArtifactDir, safetyDecision);
        }
        RolloutState promoted = rolloutManager.promoteCanary(versionRegistryPath);
        safetyGovernor.recordOutcome(governorStatePath, true, false);
        return new PipelineResult(dataset.size(), adapterArtifact, evaluation, promoted, evaluationArtifactDir, safetyDecision);
    }

    private List<TrainingExample> loadDatasetIfPresent(Path datasetPath) throws IOException {
        if (datasetPath == null || !Files.exists(datasetPath) || Files.size(datasetPath) == 0) {
            return List.of();
        }
        return objectMapper.readValue(datasetPath.toFile(), new TypeReference<List<TrainingExample>>() {
        });
    }

    private int calculatePromptDiffSize(List<TrainingExample> previous, List<TrainingExample> current) {
        Set<String> previousPrompts = new HashSet<>(previous.stream().map(TrainingExample::prompt).toList());
        Set<String> currentPrompts = new HashSet<>(current.stream().map(TrainingExample::prompt).toList());

        int changed = 0;
        for (String prompt : currentPrompts) {
            if (!previousPrompts.contains(prompt)) {
                changed++;
            }
        }
        for (String prompt : previousPrompts) {
            if (!currentPrompts.contains(prompt)) {
                changed++;
            }
        }
        return changed;
    }

    public record PipelineResult(
            int trainingExamples,
            AdapterArtifact adapterArtifact,
            GovernanceEvaluation governanceEvaluation,
            RolloutState rolloutState,
            Path evaluationArtifactDir,
            AutonomySafetyGovernor.GovernorDecision safetyDecision) {
    }
}
