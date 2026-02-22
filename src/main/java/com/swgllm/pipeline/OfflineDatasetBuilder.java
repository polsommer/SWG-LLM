package com.swgllm.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.swgllm.feedback.FeedbackCaptureService;
import com.swgllm.feedback.FeedbackRecord;

public class OfflineDatasetBuilder {
    private final FeedbackCaptureService feedbackCaptureService;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    public OfflineDatasetBuilder(FeedbackCaptureService feedbackCaptureService) {
        this.feedbackCaptureService = feedbackCaptureService;
    }

    public List<TrainingExample> buildFromApprovedFeedback(Path feedbackPath, Path outputDatasetPath) throws IOException {
        List<TrainingExample> dataset = feedbackCaptureService.load(feedbackPath).stream()
                .filter(FeedbackRecord::approvedForTraining)
                .map(record -> new TrainingExample(
                        record.anonymizedPrompt(),
                        record.anonymizedResponse(),
                        record.correctedAnswer(),
                        new TrainingExample.TrainingProvenance(
                                record.requestId(),
                                record.rating().name(),
                                record.createdAt(),
                                "feedback_capture")))
                .toList();

        if (outputDatasetPath.getParent() != null) {
            Files.createDirectories(outputDatasetPath.getParent());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputDatasetPath.toFile(), dataset);
        return dataset;
    }
}
