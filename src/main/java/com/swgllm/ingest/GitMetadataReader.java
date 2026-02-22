package com.swgllm.ingest;

import java.io.IOException;
import java.nio.file.Path;

public class GitMetadataReader {

    public String commitHash(Path repoPath) {
        return runGit(repoPath, "rev-parse", "HEAD");
    }

    public String versionTag(Path repoPath) {
        String tag = runGit(repoPath, "describe", "--tags", "--always");
        return tag.isBlank() ? "unknown" : tag;
    }

    private String runGit(Path repoPath, String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "git";
            System.arraycopy(args, 0, cmd, 1, args.length);
            Process process = new ProcessBuilder(cmd).directory(repoPath.toFile()).start();
            int exit = process.waitFor();
            if (exit != 0) {
                return "unknown";
            }
            return new String(process.getInputStream().readAllBytes()).trim();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }
}
