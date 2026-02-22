package com.swgllm.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GitCommandRunner {
    private static final int TIMEOUT_EXIT_CODE = 124;
    private static final int INTERRUPTED_EXIT_CODE = 130;
    private static final int LAUNCH_FAILURE_EXIT_CODE = 127;

    private final Duration timeout;
    private final ProcessStarter processStarter;

    public GitCommandRunner(Duration timeout) {
        this(timeout, new DefaultProcessStarter());
    }

    GitCommandRunner(Duration timeout, ProcessStarter processStarter) {
        this.timeout = timeout;
        this.processStarter = processStarter;
    }

    public GitCommandResult run(Path workingDirectory, String... command) {
        Process process;
        try {
            process = processStarter.start(workingDirectory, command);
        } catch (IOException e) {
            return new GitCommandResult(LAUNCH_FAILURE_EXIT_CODE, "", e.getMessage(), false, false, true);
        }

        CompletableFuture<String> stdoutFuture = readStream(process.getInputStream());
        CompletableFuture<String> stderrFuture = readStream(process.getErrorStream());

        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new GitCommandResult(TIMEOUT_EXIT_CODE, stdoutFuture.join(), stderrFuture.join(), true, false, false);
            }

            return new GitCommandResult(process.exitValue(), stdoutFuture.join(), stderrFuture.join(), false, false, false);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return new GitCommandResult(INTERRUPTED_EXIT_CODE, stdoutFuture.join(), stderrFuture.join(), false, true, false);
        }
    }

    private CompletableFuture<String> readStream(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = inputStream) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                return "";
            }
        });
    }

    interface ProcessStarter {
        Process start(Path workingDirectory, String... command) throws IOException;
    }

    private static final class DefaultProcessStarter implements ProcessStarter {
        @Override
        public Process start(Path workingDirectory, String... command) throws IOException {
            return new ProcessBuilder(command)
                    .directory(workingDirectory == null ? null : workingDirectory.toFile())
                    .start();
        }
    }

    @Override
    public String toString() {
        return "GitCommandRunner{" +
                "timeout=" + timeout +
                ", processStarter=" + processStarter.getClass().getSimpleName() +
                '}';
    }
}
