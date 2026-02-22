package com.swgllm.ingest;

public record GitCommandResult(
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean interrupted,
        boolean launchFailed) {

    public boolean isSuccess() {
        return !timedOut && !interrupted && !launchFailed && exitCode == 0;
    }
}
