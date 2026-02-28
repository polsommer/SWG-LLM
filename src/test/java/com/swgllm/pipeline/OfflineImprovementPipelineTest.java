package com.swgllm.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swgllm.feedback.FeedbackCaptureService;
import com.swgllm.feedback.FeedbackRating;
import com.swgllm.governance.GovernanceMetrics;
import com.swgllm.governance.GovernancePolicy;
import com.swgllm.governance.GovernanceTestResult;
import com.swgllm.versioning.ArtifactVersions;
import com.swgllm.versioning.SemanticVersion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineImprovementPipelineTest {

    @Test
    void shouldCreateRealAdapterArtifactsDuringPipelineRun() throws Exception {
        Path feedbackPath = Files.createTempFile("feedback", ".json");
        Path datasetPath = Files.createTempFile("dataset", ".json");
        Path adaptersDir = Files.createTempDirectory("adapters");
        Path rolloutPath = Files.createTempFile("rollout", ".json");
        FeedbackCaptureService capture = new FeedbackCaptureService();

        capture.capture(feedbackPath, FeedbackRating.THUMBS_UP, "prompt1", "bad1", "good1", true);
        capture.capture(feedbackPath, FeedbackRating.THUMBS_UP, "prompt2", "bad2", "good2", true);
        capture.capture(feedbackPath, FeedbackRating.THUMBS_UP, "prompt3", "bad3", "good3", true);

        OfflineImprovementPipeline pipeline = new OfflineImprovementPipeline();
        OfflineImprovementPipeline.PipelineResult result = pipeline.run(
                feedbackPath,
                datasetPath,
                adaptersDir,
                rolloutPath,
                new ArtifactVersions(new SemanticVersion(1, 0, 0), new SemanticVersion(1, 0, 0), new SemanticVersion(1, 0, 0)),
                new GovernanceMetrics(0.01, 0.01, 0.95),
                java.util.List.<GovernanceTestResult>of(),
                adaptersDir.resolve("eval-artifacts"),
                new GovernancePolicy(0.1, 0.1, 0.8));

        assertEquals(3, result.trainingExamples());
        assertTrue(Files.exists(result.adapterArtifact().weightsPath()));
        assertTrue(Files.exists(result.adapterArtifact().metadataPath()));
        assertTrue(Files.exists(result.adapterArtifact().trainingRunMetadataPath()));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode adapterMetadata = mapper.readTree(result.adapterArtifact().metadataPath().toFile());
        assertTrue(adapterMetadata.hasNonNull("adapterId"));
        assertTrue(adapterMetadata.hasNonNull("weightsPath"));
        assertEquals("adapter.weights.bin", adapterMetadata.get("weightsPath").asText());

        JsonNode runMetadata = mapper.readTree(result.adapterArtifact().trainingRunMetadataPath().toFile());
        assertTrue(runMetadata.hasNonNull("datasetHash"));
        assertEquals(3, runMetadata.get("datasetExamples").asInt());
        assertTrue(runMetadata.get("durationMillis").asLong() >= 0);
    }

    @Test
    void shouldFailWhenDatasetDoesNotPassValidationGates() throws Exception {
        Path feedbackPath = Files.createTempFile("feedback", ".json");
        Path datasetPath = Files.createTempFile("dataset", ".json");
        Path adaptersDir = Files.createTempDirectory("adapters");
        Path rolloutPath = Files.createTempFile("rollout", ".json");
        FeedbackCaptureService capture = new FeedbackCaptureService();

        capture.capture(feedbackPath, FeedbackRating.THUMBS_UP, "prompt1", "bad1", "good1", true);
        capture.capture(feedbackPath, FeedbackRating.THUMBS_UP, "prompt2", "bad2", "good2", true);

        OfflineImprovementPipeline pipeline = new OfflineImprovementPipeline();

        assertThrows(IllegalArgumentException.class, () -> pipeline.run(
                feedbackPath,
                datasetPath,
                adaptersDir,
                rolloutPath,
                new ArtifactVersions(new SemanticVersion(1, 0, 0), new SemanticVersion(1, 0, 0), new SemanticVersion(1, 0, 0)),
                new GovernanceMetrics(0.01, 0.01, 0.95),
                java.util.List.<GovernanceTestResult>of(),
                adaptersDir.resolve("eval-artifacts"),
                new GovernancePolicy(0.1, 0.1, 0.8)));
    }
}
