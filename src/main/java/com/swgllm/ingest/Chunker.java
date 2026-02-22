package com.swgllm.ingest;

import java.util.ArrayList;
import java.util.List;

public class Chunker {
    private final int maxLines;
    private final int overlapLines;

    public Chunker(int maxLines, int overlapLines) {
        this.maxLines = maxLines;
        this.overlapLines = overlapLines;
    }

    public List<DocumentChunk> chunk(ParsedArtifact artifact, String commitHash, String versionTag) {
        String[] lines = artifact.content().split("\\R", -1);
        List<DocumentChunk> chunks = new ArrayList<>();

        int start = 0;
        int chunkIndex = 0;
        while (start < lines.length) {
            int endExclusive = Math.min(lines.length, start + maxLines);
            String text = String.join("\n", java.util.Arrays.copyOfRange(lines, start, endExclusive));
            ChunkMetadata metadata = new ChunkMetadata(
                    artifact.sourcePath().toString(),
                    artifact.symbols(),
                    commitHash,
                    versionTag,
                    chunkIndex,
                    start + 1,
                    endExclusive);
            String id = artifact.sourcePath() + "#" + chunkIndex;
            chunks.add(new DocumentChunk(id, text, metadata));
            if (endExclusive == lines.length) {
                break;
            }
            start = Math.max(endExclusive - overlapLines, start + 1);
            chunkIndex++;
        }
        return chunks;
    }
}
