package com.swgllm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.swgllm.ingest.EmbeddingService;
import com.swgllm.ingest.GitRepositoryManager;
import com.swgllm.ingest.IngestionReport;
import com.swgllm.ingest.IngestionService;

import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
    void shouldRunDaemonModeWithBoundedCycleAndPersistState() throws IOException {
        Path configPath = tempDir.resolve("daemon.yml");
        Path daemonStatePath = tempDir.resolve("continuous-state.json");
        Files.writeString(configPath, """
                runtime:
                  defaultProfile: cpu-low-memory
                  profiles:
                    cpu-low-memory:
                      model: test-model
                      backend: cpu
                      contextWindowTokens: 2048
                      retrievalChunks: 4
                  timeoutMs: 30000
                continuous:
                  ingestIntervalMs: 0
                  improveIntervalMs: 0
                  maxRetries: 0
                  retryBackoffMs: 0
                  maxCycles: 1
                  maxRuntimeMs: 0
                  idleTimeoutMs: 0
                  statePath: %s
                """.formatted(daemonStatePath.toString().replace('\\', '/')));

        Main main = new Main();

        int exitCode = new CommandLine(main).execute(
                "--mode", "daemon",
                "--config", configPath.toString(),
                "--repo-path", tempDir.toString(),
                "--feedback-path", tempDir.resolve("feedback.json").toString(),
                "--dataset-path", tempDir.resolve("dataset.json").toString(),
                "--adapter-dir", tempDir.resolve("adapters").toString(),
                "--version-registry-path", tempDir.resolve("rollout.json").toString(),
                "--index-path", tempDir.resolve("index.json").toString(),
                "--state-path", tempDir.resolve("ingest-state.json").toString());

        assertEquals(0, exitCode);
        assertTrue(Files.exists(daemonStatePath));
    }

    @Test
    void shouldParseRepoUrlAndCacheDirOptions() throws IOException {
        RecordingGitRepositoryManager manager = new RecordingGitRepositoryManager(tempDir.resolve("checkout"));
        SequencingMain main = new SequencingMain(manager);
        Path cacheDir = tempDir.resolve("cache");
        Path configPath = writeTestConfig(tempDir.resolve("test-config.yml"));
        Path indexPath = tempDir.resolve("vector-index.json");
        Path statePath = tempDir.resolve("index-state.json");
        Path versionRegistryPath = tempDir.resolve("version-registry.json");

        int exitCode = new CommandLine(main).execute(
                "--mode", "ingest",
                "--repo-url", "https://github.com/SWG-Source/dsrc.git",
                "--repo-cache-dir", cacheDir.toString(),
                "--config", configPath.toString(),
                "--index-path", indexPath.toString(),
                "--state-path", statePath.toString(),
                "--version-registry-path", versionRegistryPath.toString());

        assertEquals(0, exitCode);
        assertEquals("https://github.com/SWG-Source/dsrc.git", manager.recordedRepoUrl);
        assertNotNull(manager.recordedRepoCacheDir, "Expected --repo-cache-dir to be forwarded to GitRepositoryManager");
        assertEquals(cacheDir.toAbsolutePath().normalize(), manager.recordedRepoCacheDir.toAbsolutePath().normalize());
        assertEquals(List.of("ingest"), main.events);
    }

    @Test
    void shouldFailIngestWhenRepoPathDoesNotExist() {
        Main main = new Main();
        Path missingRepo = tempDir.resolve("missing-repo");

        int exitCode = new CommandLine(main).execute("--mode", "ingest", "--repo-path", missingRepo.toString());

        assertEquals(Main.EXIT_USAGE_ERROR, exitCode);
    }

    @Test
    void shouldIncludeActionableGuidanceWhenRepoPathIsInvalid() {
        Main main = new Main();
        Path missingRepo = tempDir.resolve("missing-repo");
        main.repoPath = missingRepo;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, main::resolveRepositoryPathForIngestion);

        assertTrue(ex.getMessage().contains("Invalid --repo-path"));
        assertTrue(ex.getMessage().contains(missingRepo.toAbsolutePath().normalize().toString()));
        assertTrue(ex.getMessage().contains("--repo-url"));
        assertTrue(ex.getMessage().contains("Current working directory is"));
        assertTrue(ex.getMessage().contains("--repo-path ."));
    }

    @Test
    void shouldSuggestCaseInsensitiveMatchWhenRepoPathHasWrongCase() throws IOException {
        Main main = new Main();
        Path existingRepo = Files.createDirectories(tempDir.resolve("swg-main"));
        Path wrongCaseRepo = existingRepo.getParent().resolve("SWG-MAIN");
        main.repoPath = wrongCaseRepo;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, main::resolveRepositoryPathForIngestion);

        assertTrue(ex.getMessage().contains("Did you mean:"));
        assertTrue(ex.getMessage().contains(existingRepo.toAbsolutePath().normalize().toString()));
    }

    @Test
    void shouldPreferRepoUrlWhenBothRepoPathAndRepoUrlProvided() throws Exception {
        RecordingGitRepositoryManager manager = new RecordingGitRepositoryManager(tempDir.resolve("checkout"));
        SequencingMain main = new SequencingMain(manager);
        Path localRepo = Files.createDirectories(tempDir.resolve("local-repo"));

        int exitCode = new CommandLine(main).execute(
                "--mode", "ingest",
                "--repo-path", localRepo.toString(),
                "--repo-url", "https://example.com/repo.git");

        assertEquals(0, exitCode);
        assertEquals("https://example.com/repo.git", manager.recordedRepoUrl);
        assertTrue(Files.exists(manager.resolvedPath));
        assertEquals(List.of("ingest"), main.events);
    }

    @Test
    void shouldRunImproveAfterIngestWhenFlagEnabled() {
        Path checkout = tempDir.resolve("checkout");
        SequencingMain main = new SequencingMain(new RecordingGitRepositoryManager(checkout));

        int exitCode = new CommandLine(main).execute(
                "--mode", "ingest",
                "--repo-url", "https://example.com/repo.git",
                "--run-improve-after-ingest");

        assertEquals(0, exitCode);
        assertEquals(List.of("ingest", "improve"), main.events);
    }

    @Test
    void shouldReturnDownloadFailureExitCodeWhenRepositoryPreparationFails() {
        GitRepositoryManager manager = new FailingGitRepositoryManager(new IOException("network down"));
        Main main = new Main(manager);

        int exitCode = new CommandLine(main).execute(
                "--mode", "ingest",
                "--repo-url", "https://example.com/repo.git");

        assertEquals(Main.EXIT_DOWNLOAD_FAILURE, exitCode);
    }

    @Test
    void shouldReturnIngestFailureExitCodeWhenIngestionFails() {
        Path checkout = tempDir.resolve("checkout");
        SequencingMain main = new SequencingMain(new RecordingGitRepositoryManager(checkout));
        main.failIngest = true;

        int exitCode = new CommandLine(main).execute(
                "--mode", "ingest",
                "--repo-url", "https://example.com/repo.git");

        assertEquals(Main.EXIT_INGEST_FAILURE, exitCode);
        assertEquals(List.of("ingest"), main.events);
    }

    @Test
    void shouldReturnImproveFailureExitCodeWhenImproveAfterIngestFails() {
        Path checkout = tempDir.resolve("checkout");
        SequencingMain main = new SequencingMain(new RecordingGitRepositoryManager(checkout));
        main.failImprove = true;

        int exitCode = new CommandLine(main).execute(
                "--mode", "ingest",
                "--repo-url", "https://example.com/repo.git",
                "--run-improve-after-ingest");

        assertEquals(Main.EXIT_IMPROVE_FAILURE, exitCode);
        assertEquals(List.of("ingest", "improve"), main.events);
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
                fail("Failed to create test checkout directory: " + resolvedPath.toAbsolutePath().normalize(), e);
            }
            return resolvedPath;
        }
    }

    private Path writeTestConfig(Path configPath) throws IOException {
        try {
            return Files.writeString(configPath, """
                    runtime:
                      defaultProfile: cpu-low-memory
                      profiles:
                        cpu-low-memory:
                          model: test-model
                          backend: cpu
                          contextWindowTokens: 2048
                          retrievalChunks: 4
                      timeoutMs: 30000
                    """);
        } catch (RuntimeException e) {
            fail("Failed to create test config file: " + configPath.toAbsolutePath().normalize(), e);
            return configPath;
        }
    }

    static class FailingGitRepositoryManager extends GitRepositoryManager {
        private final IOException failure;

        FailingGitRepositoryManager(IOException failure) {
            this.failure = failure;
        }

        @Override
        public Path prepareRepository(String repoUrl, Path repoCacheDir) throws IOException {
            throw failure;
        }
    }

    static class SequencingMain extends Main {
        private final List<String> events = new ArrayList<>();
        private boolean failIngest;
        private boolean failImprove;

        SequencingMain(GitRepositoryManager gitRepositoryManager) {
            super(gitRepositoryManager);
        }

        @Override
        IngestionService createIngestionService() {
            return new IngestionService(new NoOpEmbeddingService()) {
                @Override
                public IngestionReport ingest(Path repoPath, Path indexPath, Path statePath) throws IOException {
                    events.add("ingest");
                    if (failIngest) {
                        throw new IOException("ingest failed");
                    }
                    return new IngestionReport(3, 1, 4, "abc123", "v1.0.0");
                }
            };
        }

        @Override
        void runImprovementPipeline() throws IOException {
            events.add("improve");
            if (failImprove) {
                throw new IOException("improve failed");
            }
        }
    }

    static class NoOpEmbeddingService implements EmbeddingService {

        @Override
        public String version() {
            return "test-embedding";
        }

        @Override
        public float[] embed(String text) {
            return new float[] { 0.0f, 0.0f, 0.0f };
        }

        @Override
        public int dimension() {
            return 3;
        }
    }
}
