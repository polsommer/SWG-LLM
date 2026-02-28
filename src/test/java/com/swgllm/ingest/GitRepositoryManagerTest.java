package com.swgllm.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitRepositoryManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCleanBeforeCheckoutOnExistingClone() throws Exception {
        String repoUrl = "https://example.com/org/repo.git";
        GitRepositoryManager manager = new GitRepositoryManager();
        Path checkout = tempDir.resolve(manager.repositoryDirectoryName(repoUrl));
        Files.createDirectories(checkout.resolve(".git"));

        RecordingRunner runner = new RecordingRunner(checkout);
        manager = new GitRepositoryManager(runner);

        manager.prepareRepository(repoUrl, tempDir);

        assertEquals(List.of(
                "git -C " + checkout + " fetch --prune origin",
                "git -C " + checkout + " symbolic-ref refs/remotes/origin/HEAD",
                "git -C " + checkout + " reset --hard",
                "git -C " + checkout + " clean -fd",
                "git -C " + checkout + " checkout master",
                "git -C " + checkout + " reset --hard origin/master",
                "git -C " + checkout + " clean -fd"),
                runner.commands);
    }

    private static final class RecordingRunner extends GitCommandRunner {
        private final Path checkout;
        private final List<String> commands = new ArrayList<>();

        private RecordingRunner(Path checkout) {
            super(Duration.ofSeconds(1), (workingDirectory, command) -> {
                throw new UnsupportedOperationException("process starter unused in test");
            });
            this.checkout = checkout;
        }

        @Override
        public GitCommandResult run(Path workingDirectory, String... command) {
            commands.add(String.join(" ", command));
            if (Arrays.equals(command, new String[] { "git", "-C", checkout.toString(), "symbolic-ref", "refs/remotes/origin/HEAD" })) {
                return new GitCommandResult(0, "refs/remotes/origin/master", "", false, false, false);
            }
            return new GitCommandResult(0, "", "", false, false, false);
        }
    }
}
