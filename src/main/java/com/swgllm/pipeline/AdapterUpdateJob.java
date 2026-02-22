package com.swgllm.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

public class AdapterUpdateJob {
    private static final Set<String> TOXIC_TERMS = Set.of("kill", "hate", "slur");

    private final AdapterTrainer adapterTrainer;
    private final TrainingHyperparameters hyperparameters;
    private final int minDatasetSize;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public AdapterUpdateJob() {
        this(new JavaNativeAdapterTrainer(), TrainingHyperparameters.defaults(), 3);
    }

    public AdapterUpdateJob(AdapterTrainer adapterTrainer, TrainingHyperparameters hyperparameters, int minDatasetSize) {
        this.adapterTrainer = adapterTrainer;
        this.hyperparameters = hyperparameters;
        this.minDatasetSize = minDatasetSize;
    }

    public AdapterArtifact runPeriodicUpdate(List<TrainingExample> dataset, Path adaptersDir) throws IOException {
        ValidationResult validation = validateDataset(dataset);
        String datasetHash = hashDataset(validation.cleanedDataset());

        Instant startedAt = Instant.now();
        String adapterId = "adapter-" + startedAt.toEpochMilli();
        Path runDir = adaptersDir.resolve(adapterId);
        Files.createDirectories(runDir);

        AdapterTrainer.TrainingResult trainingResult = adapterTrainer.train(
                validation.cleanedDataset(),
                runDir,
                hyperparameters,
                datasetHash);

        long durationMillis = Duration.between(startedAt, Instant.now()).toMillis();

        TrainingRunMetadata runMetadata = new TrainingRunMetadata(
                adapterId,
                startedAt,
                durationMillis,
                datasetHash,
                dataset.size(),
                validation.cleanedDataset().size(),
                hyperparameters,
                trainingResult.trainerName(),
                trainingResult.notes());

        Path runMetadataPath = runDir.resolve("training-run.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(runMetadataPath.toFile(), runMetadata);

        AdapterMetadata adapterMetadata = new AdapterMetadata(
                adapterId,
                Instant.now(),
                validation.cleanedDataset().size(),
                datasetHash,
                runDir.relativize(trainingResult.weightsPath()).toString(),
                runDir.relativize(runMetadataPath).toString(),
                trainingResult.trainerName());

        Path adapterMetadataPath = runDir.resolve("adapter-metadata.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(adapterMetadataPath.toFile(), adapterMetadata);

        return new AdapterArtifact(adapterId, adapterMetadataPath, trainingResult.weightsPath(), runMetadataPath);
    }

    private ValidationResult validateDataset(List<TrainingExample> dataset) {
        if (dataset.size() < minDatasetSize) {
            throw new IllegalArgumentException("Dataset too small. Expected at least " + minDatasetSize);
        }

        Map<String, TrainingExample> deduped = new LinkedHashMap<>();
        for (TrainingExample example : dataset) {
            if (!isQualityExample(example) || containsToxicContent(example)) {
                continue;
            }
            String dedupeKey = (example.prompt() + "\n" + example.correctedAnswer()).strip().toLowerCase();
            deduped.putIfAbsent(dedupeKey, example);
        }

        if (deduped.size() < minDatasetSize) {
            throw new IllegalArgumentException("Dataset did not pass quality, dedupe, and toxicity gates");
        }

        return new ValidationResult(List.copyOf(deduped.values()));
    }

    private boolean isQualityExample(TrainingExample example) {
        return example.prompt() != null
                && !example.prompt().isBlank()
                && example.correctedAnswer() != null
                && !example.correctedAnswer().isBlank()
                && example.provenance() != null
                && example.provenance().feedbackRequestId() != null
                && !example.provenance().feedbackRequestId().isBlank();
    }

    private boolean containsToxicContent(TrainingExample example) {
        String merged = (example.prompt() + " " + example.chosenAnswer() + " " + example.correctedAnswer()).toLowerCase();
        return TOXIC_TERMS.stream().anyMatch(merged::contains);
    }

    private String hashDataset(List<TrainingExample> dataset) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (TrainingExample example : dataset) {
                digest.update(example.prompt().getBytes(StandardCharsets.UTF_8));
                digest.update(example.chosenAnswer().getBytes(StandardCharsets.UTF_8));
                digest.update(example.correctedAnswer().getBytes(StandardCharsets.UTF_8));
                digest.update(example.provenance().feedbackRequestId().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256 algorithm", e);
        }
    }

    private record ValidationResult(List<TrainingExample> cleanedDataset) {
    }
}
