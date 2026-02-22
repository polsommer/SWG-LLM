package com.swgllm.ingest;

import java.util.List;

public record ChunkMetadata(
        String sourcePath,
        List<String> symbols,
        String commitHash,
        String versionTag,
        int chunkIndex,
        int startLine,
        int endLine) {
}
