package com.swgllm.ingest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitCommandRunnerTest {

    @Test
    void shouldCaptureStdoutAndStderrForNonZeroExit() {
        GitCommandRunner runner = new GitCommandRunner(Duration.ofSeconds(1),
                (workingDirectory, command) -> new FakeProcess(2, true, false, "out", "err", false));

        GitCommandResult result = runner.run(Path.of("."), "git", "status");

        assertEquals(2, result.exitCode());
        assertEquals("out", result.stdout());
        assertEquals("err", result.stderr());
        assertFalse(result.isSuccess());
    }

    @Test
    void shouldMarkInterruptedAndReinterruptThread() {
        GitCommandRunner runner = new GitCommandRunner(Duration.ofSeconds(1),
                (workingDirectory, command) -> new FakeProcess(0, true, true, "", "", false));

        GitCommandResult result = runner.run(Path.of("."), "git", "status");

        assertTrue(result.interrupted());
        assertEquals(130, result.exitCode());
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    @Test
    void shouldMarkTimedOutAndDestroyProcess() {
        FakeProcess process = new FakeProcess(0, false, false, "", "timeout", false);
        GitCommandRunner runner = new GitCommandRunner(Duration.ofMillis(5), (workingDirectory, command) -> process);

        GitCommandResult result = runner.run(Path.of("."), "git", "fetch");

        assertTrue(result.timedOut());
        assertEquals(124, result.exitCode());
        assertTrue(process.destroyForciblyCalled);
    }

    private static class FakeProcess extends Process {
        private final int exitCode;
        private final boolean waitFinished;
        private final boolean interruptedWait;
        private final InputStream stdout;
        private final InputStream stderr;
        private final boolean alive;

        private boolean destroyForciblyCalled;

        private FakeProcess(int exitCode,
                boolean waitFinished,
                boolean interruptedWait,
                String stdout,
                String stderr,
                boolean alive) {
            this.exitCode = exitCode;
            this.waitFinished = waitFinished;
            this.interruptedWait = interruptedWait;
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
            this.alive = alive;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (interruptedWait) {
                throw new InterruptedException("interrupted");
            }
            return waitFinished;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            // no-op
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
