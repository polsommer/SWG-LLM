package com.swgllm.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swgllm.feedback.FeedbackCaptureService;
import com.swgllm.feedback.FeedbackRating;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OfflineDatasetBuilderTest {

    @Test
    void shouldBuildDatasetOnlyFromApprovedFeedback() throws Exception {
        Path feedbackPath = Files.createTempFile("feedback", ".json");
        Path datasetPath = Files.createTempFile("dataset", ".json");
        FeedbackCaptureService captureService = new FeedbackCaptureService();

        captureService.capture(feedbackPath, null, "telemetry prompt", "telemetry response", null, false);
        captureService.capture(feedbackPath, FeedbackRating.THUMBS_DOWN, "p1", "r1", "user correction", true);
        captureService.capture(feedbackPath, FeedbackRating.THUMBS_UP, "p2", "r2", "", false);

        OfflineDatasetBuilder builder = new OfflineDatasetBuilder(captureService);
        List<TrainingExample> examples = builder.buildFromApprovedFeedback(feedbackPath, datasetPath);

        assertEquals(1, examples.size());
        assertEquals("p1", examples.getFirst().prompt());
        assertEquals("r1", examples.getFirst().chosenAnswer());
        assertEquals("user correction", examples.getFirst().correctedAnswer());
        assertNotNull(examples.getFirst().provenance());
        assertNotNull(examples.getFirst().provenance().feedbackRequestId());
        assertEquals("THUMBS_DOWN", examples.getFirst().provenance().feedbackRating());

        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        TrainingExample[] persisted = mapper.readValue(datasetPath.toFile(), TrainingExample[].class);
        assertEquals(1, persisted.length);
        assertEquals("feedback_capture", persisted[0].provenance().source());
    }

    @Test
    void shouldUseUnratedProvenanceWhenApprovedFeedbackHasNoRating() throws Exception {
        Path feedbackPath = Files.createTempFile("feedback-unrated", ".json");
        Path datasetPath = Files.createTempFile("dataset-unrated", ".json");
        FeedbackCaptureService captureService = new FeedbackCaptureService();

        captureService.capture(feedbackPath, null, "p", "r", "c", true);

        OfflineDatasetBuilder builder = new OfflineDatasetBuilder(captureService);
        List<TrainingExample> examples = builder.buildFromApprovedFeedback(feedbackPath, datasetPath);

        assertEquals(1, examples.size());
        assertEquals("UNRATED", examples.getFirst().provenance().feedbackRating());
    }

}
