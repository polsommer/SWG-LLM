package com.swgllm;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.swgllm.ingest.GitRepositoryManager;

import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldParseRepoUrlAndCacheDirOptions() {
        RecordingGitRepositoryManager manager = new RecordingGitRepositoryManager(tempDir.resolve("checkout"));
        Main main = new Main(manager);

        int exitCode = new CommandLine(main).execute(
                "--mode", "ingest",
                "--repo-url", "https://github.com/SWG-Source/dsrc.git",
                "--repo-cache-dir", tempDir.resolve("cache").toString());

        assertEquals(0, exitCode);
        assertEquals("https://github.com/SWG-Source/dsrc.git", manager.recordedRepoUrl);
        assertEquals(tempDir.resolve("cache"), manager.recordedRepoCacheDir);
    }

    @Test
    void shouldFailIngestWhenRepoPathDoesNotExist() {
        Main main = new Main();
        Path missingRepo = tempDir.resolve("missing-repo");

        int exitCode = new CommandLine(main).execute("--mode", "ingest", "--repo-path", missingRepo.toString());

        assertEquals(2, exitCode);
    }

    @Test
    void shouldPreferRepoUrlWhenBothRepoPathAndRepoUrlProvided() throws Exception {
        RecordingGitRepositoryManager manager = new RecordingGitRepositoryManager(tempDir.resolve("checkout"));
        Main main = new Main(manager);
        Path localRepo = Files.createDirectories(tempDir.resolve("local-repo"));

        int exitCode = new CommandLine(main).execute(
                "--mode", "ingest",
                "--repo-path", localRepo.toString(),
                "--repo-url", "https://example.com/repo.git");

        assertEquals(0, exitCode);
        assertEquals("https://example.com/repo.git", manager.recordedRepoUrl);
        assertTrue(Files.exists(manager.resolvedPath));
    }

    static class RecordingGitRepositoryManager extends GitRepositoryManager {
        private final Path resolvedPath;
        private String recordedRepoUrl;
        private Path recordedRepoCacheDir;

        RecordingGitRepositoryManager(Path resolvedPath) {
            this.resolvedPath = resolvedPath;
        }

        @Override
        public Path prepareRepository(String repoUrl, Path repoCacheDir) {
            this.recordedRepoUrl = repoUrl;
            this.recordedRepoCacheDir = repoCacheDir;
            try {
                Files.createDirectories(resolvedPath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return resolvedPath;
        }
    }
}
