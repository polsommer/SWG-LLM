package com.swgllm;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseArguments() {
        Main main = new Main();
        int exitCode = new CommandLine(main).execute("--mode", "benchmark");
        assertEquals(0, exitCode);
    }


    @Test
    void shouldFailIngestWhenRepoPathDoesNotExist() {
        Main main = new Main();
        Path missingRepo = tempDir.resolve("missing-repo");

        int exitCode = new CommandLine(main).execute("--mode", "ingest", "--repo-path", missingRepo.toString());

        assertEquals(2, exitCode);
    }

}
