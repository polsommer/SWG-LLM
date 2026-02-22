package com.swgllm.pipeline;

import java.time.Instant;

public record AdapterMetadata(
        String adapterId,
        Instant createdAt,
        int trainingExamples,
        String datasetHash,
        String weightsPath,
        String runMetadataPath,
        String trainerName) {
}
