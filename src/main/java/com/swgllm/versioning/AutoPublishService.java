package com.swgllm.versioning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.swgllm.ingest.GitCommandResult;
import com.swgllm.ingest.GitCommandRunner;
import com.swgllm.runtime.AppConfig;

public class AutoPublishService {
    private final CommandExecutor commandExecutor;
    private final AutoPublishAuditLog auditLog;
    private final Clock clock;

    public AutoPublishService(GitCommandRunner gitCommandRunner) {
        this((workingDirectory, command) -> gitCommandRunner.run(workingDirectory, command), new AutoPublishAuditLog(), Clock.systemUTC());
    }

    AutoPublishService(CommandExecutor commandExecutor, AutoPublishAuditLog auditLog, Clock clock) {
        this.commandExecutor = commandExecutor;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public PublishResult publish(PublishRequest request, AppConfig.AutoPublishConfig policy) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(policy, "policy");

        Instant timestamp = clock.instant();
        String outcome = "BLOCKED";
        String details = "Policy gate blocked publish attempt";
        String commitHash = "";

        try {
            String policyViolation = policyViolation(request, policy, timestamp);
            if (policyViolation != null) {
                details = policyViolation;
                return new PublishResult(false, false, false, details, "", "", request.workspaceRoot());
            }

            Path localRepo = prepareRepository(request, policy);
            applyArtifacts(request.generatedArtifactsDir(), localRepo);
            runValidationChecks(request.validationCommands(), localRepo);

            String commitMessage = request.commitMessage().isBlank() ? defaultCommitMessage(request) : request.commitMessage();
            boolean hasDiff = hasChanges(localRepo);
            if (!hasDiff) {
                outcome = "NO_CHANGES";
                details = "No repository changes detected after applying generated artifacts";
                return new PublishResult(true, false, false, details, "", commitMessage, localRepo);
            }

            if (request.dryRun() || policy.isDryRun()) {
                outcome = "DRY_RUN";
                details = "Dry-run enabled: commit and push skipped";
                return new PublishResult(true, true, false, details, "", commitMessage, localRepo);
            }

            createCommit(localRepo, commitMessage);
            commitHash = runRequired(localRepo, "git", "rev-parse", "HEAD").stdout();

            if (!request.governancePassed() || !request.evaluationPassed()) {
                outcome = "LOCAL_COMMIT_ONLY";
                details = "Governance/evaluation checks failed; push was blocked";
                return new PublishResult(true, true, false, details, commitHash, commitMessage, localRepo);
            }

            runRequired(localRepo, "git", "push", "origin", request.branch());
            outcome = "PUSHED";
            details = "Changes pushed successfully";
            return new PublishResult(true, true, true, details, commitHash, commitMessage, localRepo);
        } finally {
            AutoPublishAuditEntry entry = new AutoPublishAuditEntry(
                    timestamp,
                    request.actor(),
                    request.reason(),
                    request.targetRepoUrl(),
                    request.branch(),
                    request.dryRun() || policy.isDryRun(),
                    request.governancePassed(),
                    request.evaluationPassed(),
                    request.highImpactChange(),
                    request.manualApprovalProvided(),
                    request.scoreDelta(),
                    Map.copyOf(request.metrics()),
                    outcome,
                    details,
                    request.commitMessage().isBlank() ? defaultCommitMessage(request) : request.commitMessage(),
                    commitHash);
            auditLog.append(request.auditLogPath(), entry);
        }
    }

    private String policyViolation(PublishRequest request, AppConfig.AutoPublishConfig policy, Instant now) throws IOException {
        if (!policy.isEnabled()) {
            return "autopublish is disabled";
        }
        if (!policy.getAllowedBranches().contains(request.branch())) {
            return "branch is not in autopublish.allowedBranches";
        }
        if (request.scoreDelta() < policy.getRequiredMinScoreDelta()) {
            return String.format(Locale.ROOT, "score delta %.4f is below required minimum %.4f", request.scoreDelta(), policy.getRequiredMinScoreDelta());
        }
        if (policy.isManualApprovalForHighImpactChanges() && request.highImpactChange() && !request.manualApprovalProvided()) {
            return "manual approval is required for high-impact changes";
        }

        Instant startOfDay = LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC);
        long pushesToday = auditLog.countSuccessfulPushesSince(request.auditLogPath(), startOfDay);
        if (pushesToday >= policy.getMaxPushesPerDay()) {
            return "max pushes/day gate reached";
        }
        return null;
    }

    private Path prepareRepository(PublishRequest request, AppConfig.AutoPublishConfig policy) throws IOException {
        Path workspaceRoot = request.workspaceRoot() == null ? Path.of(policy.getWorkspacePath()) : request.workspaceRoot();
        Files.createDirectories(workspaceRoot);

        String repoName = repoName(request.targetRepoUrl());
        Path repoPath = workspaceRoot.resolve(repoName);
        if (Files.exists(repoPath.resolve(".git"))) {
            runRequired(repoPath, "git", "fetch", "origin");
            runRequired(repoPath, "git", "checkout", request.branch());
            runRequired(repoPath, "git", "pull", "--ff-only", "origin", request.branch());
        } else {
            runRequired(workspaceRoot, "git", "clone", "--branch", request.branch(), request.targetRepoUrl(), repoName);
        }
        return repoPath;
    }

    private void applyArtifacts(Path artifactDirectory, Path repoPath) throws IOException {
        if (artifactDirectory == null || !Files.exists(artifactDirectory)) {
            return;
        }
        List<Path> files;
        try (var stream = Files.walk(artifactDirectory)) {
            files = stream.filter(Files::isRegularFile).toList();
        }

        for (Path source : files) {
            Path relative = artifactDirectory.relativize(source);
            Path target = repoPath.resolve(relative);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void runValidationChecks(List<String> validationCommands, Path repoPath) {
        for (String command : validationCommands) {
            runRequired(repoPath, "bash", "-lc", command);
        }
    }

    private boolean hasChanges(Path repoPath) {
        runRequired(repoPath, "git", "add", "-A");
        GitCommandResult status = runRequired(repoPath, "git", "status", "--porcelain");
        return !status.stdout().isBlank();
    }

    private void createCommit(Path repoPath, String commitMessage) {
        String[] lines = commitMessage.split("\\R", -1);
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("commit");
        command.add("-m");
        command.add(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                command.add("-m");
                command.add(lines[i]);
            }
        }
        runRequired(repoPath, command.toArray(String[]::new));
    }

    private String defaultCommitMessage(PublishRequest request) {
        return "autopublish(improvement): " + request.reason() + "\n\n"
                + "actor: " + request.actor() + "\n"
                + "scoreDelta: " + String.format(Locale.ROOT, "%.4f", request.scoreDelta()) + "\n"
                + "governance: " + request.governancePassed() + "\n"
                + "evaluation: " + request.evaluationPassed() + "\n"
                + "highImpact: " + request.highImpactChange();
    }

    private GitCommandResult runRequired(Path workingDirectory, String... command) {
        GitCommandResult result = commandExecutor.run(workingDirectory, command);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + " stderr=" + result.stderr());
        }
        return result;
    }

    private String repoName(String targetRepoUrl) {
        String normalized = targetRepoUrl.endsWith(".git")
                ? targetRepoUrl.substring(0, targetRepoUrl.length() - 4)
                : targetRepoUrl;
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    @FunctionalInterface
    interface CommandExecutor {
        GitCommandResult run(Path workingDirectory, String... command);
    }

    public record PublishRequest(
            String targetRepoUrl,
            Path workspaceRoot,
            Path generatedArtifactsDir,
            List<String> validationCommands,
            String branch,
            String actor,
            String reason,
            String commitMessage,
            boolean governancePassed,
            boolean evaluationPassed,
            boolean highImpactChange,
            boolean manualApprovalProvided,
            double scoreDelta,
            Map<String, Double> metrics,
            boolean dryRun,
            Path auditLogPath) {
        public PublishRequest {
            targetRepoUrl = targetRepoUrl == null || targetRepoUrl.isBlank()
                    ? "https://github.com/polsommer/llm-dsrc.git"
                    : targetRepoUrl;
            validationCommands = validationCommands == null ? List.of() : List.copyOf(validationCommands);
            branch = branch == null || branch.isBlank() ? "main" : branch;
            actor = actor == null || actor.isBlank() ? "system" : actor;
            reason = reason == null || reason.isBlank() ? "generated-improvement" : reason;
            commitMessage = commitMessage == null ? "" : commitMessage;
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
            auditLogPath = auditLogPath == null ? Path.of(".swgllm/autopublish-audit.log") : auditLogPath;
        }
    }

    public record PublishResult(
            boolean policyPassed,
            boolean commitCreated,
            boolean pushed,
            String details,
            String commitHash,
            String commitMessage,
            Path localRepositoryPath) {
    }
}
