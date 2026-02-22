package com.swgllm.ingest;

import java.nio.file.Path;
import java.util.List;

public record ParsedArtifact(Path sourcePath, String content, List<String> symbols) {
}
