package com.swgllm.ingest;

import java.nio.file.Path;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitMetadataReader {
    private static final Logger log = LoggerFactory.getLogger(GitMetadataReader.class);

    private final GitCommandRunner gitCommandRunner;

    public GitMetadataReader() {
        this(new GitCommandRunner(Duration.ofSeconds(10)));
    }

    GitMetadataReader(GitCommandRunner gitCommandRunner) {
        this.gitCommandRunner = gitCommandRunner;
    }

    public String commitHash(Path repoPath) {
        return runGit(repoPath, "git", "rev-parse", "HEAD");
    }

    public String versionTag(Path repoPath) {
        String tag = runGit(repoPath, "git", "describe", "--tags", "--always");
        return tag.isBlank() ? "unknown" : tag;
    }

    private String runGit(Path repoPath, String... command) {
        GitCommandResult result = gitCommandRunner.run(repoPath, command);
        if (!result.isSuccess()) {
            log.warn("git metadata command failed exitCode={} timedOut={} interrupted={} stderr={}",
                    result.exitCode(), result.timedOut(), result.interrupted(), result.stderr());
            return "unknown";
        }
        return result.stdout().trim();
    }
}
