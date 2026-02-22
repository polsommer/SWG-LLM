package com.swgllm.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IngestionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldIngestAndIncrementallySkipUnchangedFiles() throws Exception {
        Path repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        Files.writeString(repo.resolve("README.md"), "# Heading\nhello world\n");
        Files.writeString(repo.resolve("Example.java"), "public class Example { void go() {} }\n");

        IngestionService service = new IngestionService(new HashingEmbeddingService(64));
        Path index = tempDir.resolve("vector-index.json");
        Path state = tempDir.resolve("state.json");

        IngestionReport first = service.ingest(repo, index, state);
        assertEquals(2, first.processedFiles());

        IngestionReport second = service.ingest(repo, index, state);
        assertEquals(0, second.processedFiles());
        assertEquals(2, second.skippedFiles());

        RetrievalService retrieval = new RetrievalService(new HashingEmbeddingService(64));
        List<SearchResult> results = retrieval.retrieve(index, "example class", 2);
        assertFalse(results.isEmpty());
        assertFalse(results.getFirst().citationSnippet().isBlank());
    }
}
