package com.swgllm.pipeline;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface AdapterTrainer {
    TrainingResult train(
            List<TrainingExample> dataset,
            Path runDirectory,
            TrainingHyperparameters hyperparameters,
            String datasetHash) throws IOException;

    record TrainingResult(Path weightsPath, String trainerName, String notes) {
    }
}
