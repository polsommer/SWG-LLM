package com.swgllm.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitRepositoryManager {
    private static final Logger log = LoggerFactory.getLogger(GitRepositoryManager.class);

    private final GitCommandRunner gitCommandRunner;

    public GitRepositoryManager() {
        this(new GitCommandRunner(Duration.ofSeconds(60)));
    }

    GitRepositoryManager(GitCommandRunner gitCommandRunner) {
        this.gitCommandRunner = gitCommandRunner;
    }

    public Path prepareRepository(String repoUrl, Path repoCacheDir) throws IOException, InterruptedException {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl must not be blank");
        }

        Files.createDirectories(repoCacheDir);
        Path checkoutPath = repoCacheDir.resolve(repositoryDirectoryName(repoUrl));

        if (!Files.isDirectory(checkoutPath.resolve(".git"))) {
            runCommand(null, "git", "clone", repoUrl, checkoutPath.toString());
            return checkoutPath;
        }

        runCommand(null, "git", "-C", checkoutPath.toString(), "fetch", "--prune", "origin");
        String defaultBranch = resolveDefaultBranch(checkoutPath);
        runCommand(null, "git", "-C", checkoutPath.toString(), "reset", "--hard");
        runCommand(null, "git", "-C", checkoutPath.toString(), "clean", "-fd");
        runCommand(null, "git", "-C", checkoutPath.toString(), "checkout", defaultBranch);
        runCommand(null, "git", "-C", checkoutPath.toString(), "reset", "--hard", "origin/" + defaultBranch);
        runCommand(null, "git", "-C", checkoutPath.toString(), "clean", "-fd");

        return checkoutPath;
    }

    String resolveDefaultBranch(Path checkoutPath) throws IOException, InterruptedException {
        String ref = runCommand(null, "git", "-C", checkoutPath.toString(), "symbolic-ref", "refs/remotes/origin/HEAD").trim();
        int slash = ref.lastIndexOf('/');
        return slash >= 0 ? ref.substring(slash + 1) : ref;
    }

    String repositoryDirectoryName(String repoUrl) {
        String normalized = repoUrl.strip();
        String baseName = normalized;
        int slashIdx = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf(':'));
        if (slashIdx >= 0 && slashIdx < baseName.length() - 1) {
            baseName = baseName.substring(slashIdx + 1);
        }
        if (baseName.endsWith(".git")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        baseName = baseName.replaceAll("[^A-Za-z0-9._-]", "-");
        if (baseName.isBlank()) {
            baseName = "repo";
        }
        return baseName + "-" + shortSha256(normalized);
    }

    private String shortSha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String runCommand(Path workingDirectory, String... command) throws IOException, InterruptedException {
        GitCommandResult result = gitCommandRunner.run(workingDirectory, command);
        if (result.interrupted()) {
            throw new InterruptedException("Command interrupted: " + String.join(" ", command));
        }
        if (!result.isSuccess()) {
            log.error("git command failed command='{}' exitCode={} timedOut={} stderr={}",
                    String.join(" ", command), result.exitCode(), result.timedOut(), result.stderr());
            throw new IOException("Command failed (" + String.join(" ", command) + ") exitCode=" + result.exitCode()
                    + " timedOut=" + result.timedOut() + " stderr=" + result.stderr());
        }
        return result.stdout();
    }
}
