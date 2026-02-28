package com.swgllm.versioning;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.swgllm.ingest.GitCommandResult;
import com.swgllm.runtime.AppConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPublishServiceTest {

    @Test
    void shouldBlockWhenPolicyFailsAndLogAuditEntry() throws Exception {
        Path tempDir = Files.createTempDirectory("autopublish-policy");
        Path auditPath = tempDir.resolve("audit.log");
        AutoPublishAuditLog auditLog = new AutoPublishAuditLog();

        AutoPublishService service = new AutoPublishService(
                (workingDirectory, command) -> new GitCommandResult(0, "", "", false, false, false),
                auditLog,
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));

        AppConfig.AutoPublishConfig config = new AppConfig.AutoPublishConfig();
        config.setEnabled(false);
        config.setDryRun(false);

        AutoPublishService.PublishResult result = service.publish(new AutoPublishService.PublishRequest(
                "https://github.com/polsommer/llm-dsrc.git",
                tempDir,
                null,
                List.of(),
                "main",
                "tester",
                "insufficient-score",
                "",
                true,
                true,
                false,
                true,
                0.10,
                Map.of("quality", 0.88),
                false,
                auditPath), config);

        assertFalse(result.policyPassed());
        assertFalse(result.commitCreated());
        assertFalse(result.pushed());
        assertEquals(1, auditLog.readAll(auditPath).size());
        assertEquals("BLOCKED", auditLog.readAll(auditPath).getFirst().outcome());
    }

    @Test
    void shouldPerformDryRunWithoutCommitOrPush() throws Exception {
        Path tempDir = Files.createTempDirectory("autopublish-dry");
        Path artifactsDir = tempDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);
        Files.writeString(artifactsDir.resolve("README.md"), "new content");
        Path auditPath = tempDir.resolve("audit.log");

        List<String> commands = List.of(
                "git clone --branch main https://example.com/repo.git repo",
                "git add -A",
                "git status --porcelain");
        StubCommandExecutor executor = new StubCommandExecutor(commands);
        executor.register("git clone --branch main https://example.com/repo.git repo", new GitCommandResult(0, "", "", false, false, false));
        executor.register("git add -A", new GitCommandResult(0, "", "", false, false, false));
        executor.register("git status --porcelain", new GitCommandResult(0, "M README.md", "", false, false, false));

        AutoPublishService service = new AutoPublishService(
                executor,
                new AutoPublishAuditLog(),
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC));

        AppConfig.AutoPublishConfig config = new AppConfig.AutoPublishConfig();
        config.setEnabled(true);
        config.setAllowedBranches(List.of("main"));
        config.setDryRun(true);
        config.setWorkspacePath(tempDir.resolve("workspace").toString());
        config.setTargetRepoUrl("https://example.com/repo.git");

        AutoPublishService.PublishResult result = service.publish(new AutoPublishService.PublishRequest(
                "https://example.com/repo.git",
                tempDir.resolve("workspace"),
                artifactsDir,
                List.of(),
                "main",
                "tester",
                "update-doc",
                "",
                true,
                true,
                false,
                true,
                0.12,
                Map.of("eval", 0.91),
                false,
                auditPath), config);

        assertTrue(result.policyPassed());
        assertTrue(result.commitCreated());
        assertFalse(result.pushed());
        assertEquals("DRY_RUN", new AutoPublishAuditLog().readAll(auditPath).getFirst().outcome());
    }

    private static class StubCommandExecutor implements AutoPublishService.CommandExecutor {
        private final List<String> expected;
        private final Map<String, GitCommandResult> responses = new HashMap<>();
        private int index = 0;

        private StubCommandExecutor(List<String> expected) {
            this.expected = expected;
        }

        private void register(String command, GitCommandResult result) {
            responses.put(command, result);
        }

        @Override
        public GitCommandResult run(Path workingDirectory, String... command) {
            String flattened = String.join(" ", command);
            assertEquals(expected.get(index), flattened);
            index++;
            return responses.getOrDefault(flattened, new GitCommandResult(0, "", "", false, false, false));
        }
    }
}
