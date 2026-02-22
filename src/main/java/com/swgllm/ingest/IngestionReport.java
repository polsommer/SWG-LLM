package com.swgllm.ingest;

public record IngestionReport(int processedFiles, int skippedFiles, int totalFiles, String commitHash, String versionTag) {
}
