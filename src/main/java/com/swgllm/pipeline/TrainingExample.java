package com.swgllm.pipeline;

import java.time.Instant;

public record TrainingExample(
        String prompt,
        String chosenAnswer,
        String correctedAnswer,
        TrainingProvenance provenance) {

    public record TrainingProvenance(
            String feedbackRequestId,
            String feedbackRating,
            Instant feedbackCreatedAt,
            String source) {
    }
}
