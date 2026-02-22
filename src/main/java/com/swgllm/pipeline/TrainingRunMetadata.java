package com.swgllm.pipeline;

import java.time.Instant;

public record TrainingRunMetadata(
        String runId,
        Instant startedAt,
        long durationMillis,
        String datasetHash,
        int datasetExamples,
        int deduplicatedExamples,
        TrainingHyperparameters hyperparameters,
        String trainerName,
        String notes) {
}
