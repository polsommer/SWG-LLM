package com.swgllm.pipeline;

import java.nio.file.Path;

public record AdapterArtifact(
        String adapterId,
        Path metadataPath,
        Path weightsPath,
        Path trainingRunMetadataPath) {
}
