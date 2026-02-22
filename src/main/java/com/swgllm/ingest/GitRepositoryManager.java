package com.swgllm.ingest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class GitRepositoryManager {

    public Path prepareRepository(String repoUrl, Path repoCacheDir) throws IOException, InterruptedException {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("repoUrl must not be blank");
        }

        Files.createDirectories(repoCacheDir);
        Path checkoutPath = repoCacheDir.resolve(repositoryDirectoryName(repoUrl));

        if (!Files.isDirectory(checkoutPath.resolve(".git"))) {
            runCommand("git", "clone", repoUrl, checkoutPath.toString());
            return checkoutPath;
        }

        runCommand("git", "-C", checkoutPath.toString(), "fetch", "--prune", "origin");
        String defaultBranch = resolveDefaultBranch(checkoutPath);
        runCommand("git", "-C", checkoutPath.toString(), "checkout", defaultBranch);
        runCommand("git", "-C", checkoutPath.toString(), "reset", "--hard", "origin/" + defaultBranch);
        runCommand("git", "-C", checkoutPath.toString(), "clean", "-fd");

        return checkoutPath;
    }

    String resolveDefaultBranch(Path checkoutPath) throws IOException, InterruptedException {
        String ref = runCommand("git", "-C", checkoutPath.toString(), "symbolic-ref", "refs/remotes/origin/HEAD").trim();
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

    private String runCommand(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output;
        try (java.io.InputStream inputStream = process.getInputStream()) {
            output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed (" + String.join(" ", command) + "):\n" + output.trim());
        }
        return output;
    }
}
