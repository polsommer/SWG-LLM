package com.swgllm.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class AdapterUpdateJob {

    public Path runPeriodicUpdate(List<TrainingExample> dataset, Path adaptersDir) throws IOException {
        Files.createDirectories(adaptersDir);
        String adapterVersion = "adapter-" + Instant.now().toEpochMilli();
        Path artifact = adaptersDir.resolve(adapterVersion + ".bin");
        String payload = "examples=" + dataset.size() + "\ncreatedAt=" + Instant.now();
        Files.writeString(artifact, payload);
        return artifact;
    }
}
