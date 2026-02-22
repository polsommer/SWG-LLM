package com.swgllm.pipeline;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.swgllm.feedback.FeedbackCaptureService;
import com.swgllm.feedback.FeedbackRating;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OfflineDatasetBuilderTest {

    @Test
    void shouldBuildDatasetOnlyFromApprovedFeedback() throws Exception {
        Path feedbackPath = Files.createTempFile("feedback", ".json");
        Path datasetPath = Files.createTempFile("dataset", ".json");
        FeedbackCaptureService captureService = new FeedbackCaptureService();

        captureService.capture(feedbackPath, FeedbackRating.THUMBS_UP, "p1", "r1", "c1", true);
        captureService.capture(feedbackPath, FeedbackRating.THUMBS_DOWN, "p2", "r2", "c2", false);

        OfflineDatasetBuilder builder = new OfflineDatasetBuilder(captureService);
        List<TrainingExample> examples = builder.buildFromApprovedFeedback(feedbackPath, datasetPath);

        assertEquals(1, examples.size());
        assertEquals("p1", examples.getFirst().prompt());
    }
}
