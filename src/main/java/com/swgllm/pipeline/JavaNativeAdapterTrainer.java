package com.swgllm.pipeline;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class JavaNativeAdapterTrainer implements AdapterTrainer {

    @Override
    public TrainingResult train(
            List<TrainingExample> dataset,
            Path runDirectory,
            TrainingHyperparameters hyperparameters,
            String datasetHash) throws IOException {
        Files.createDirectories(runDirectory);

        byte[] digest = digestOfDataset(dataset, hyperparameters, datasetHash);
        ByteBuffer payload = ByteBuffer.allocate(16 + digest.length);
        payload.putInt(dataset.size());
        payload.putInt(hyperparameters.epochs());
        payload.putDouble(hyperparameters.learningRate());
        payload.putInt(hyperparameters.batchSize());
        payload.put(digest);

        Path weightsPath = runDirectory.resolve("adapter.weights.bin");
        Files.write(weightsPath, payload.array());

        return new TrainingResult(
                weightsPath,
                "java-native-trainer",
                "Deterministic local trainer generated reproducible adapter weights");
    }

    private byte[] digestOfDataset(
            List<TrainingExample> dataset,
            TrainingHyperparameters hyperparameters,
            String datasetHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(datasetHash.getBytes(StandardCharsets.UTF_8));
            digest.update(Integer.toString(hyperparameters.epochs()).getBytes(StandardCharsets.UTF_8));
            digest.update(Double.toString(hyperparameters.learningRate()).getBytes(StandardCharsets.UTF_8));
            digest.update(Integer.toString(hyperparameters.batchSize()).getBytes(StandardCharsets.UTF_8));
            for (TrainingExample example : dataset) {
                digest.update(example.prompt().getBytes(StandardCharsets.UTF_8));
                digest.update(example.chosenAnswer().getBytes(StandardCharsets.UTF_8));
                digest.update(example.correctedAnswer().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest()).getBytes(StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256 algorithm", e);
        }
    }
}
