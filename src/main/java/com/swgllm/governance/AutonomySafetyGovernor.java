package com.swgllm.governance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class AutonomySafetyGovernor {
    private final GovernorPolicy policy;
    private final Clock clock;
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    public AutonomySafetyGovernor() {
        this(new GovernorPolicy(3, 0.02, 500, 200, Duration.ofMinutes(30), "quarantine/uncertain-improvements"), Clock.systemUTC());
    }

    public AutonomySafetyGovernor(GovernorPolicy policy, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public GovernorDecision evaluateTrainingCycle(TrainingCycleContext context) throws IOException {
        Objects.requireNonNull(context, "context");
        GovernorState state = loadState(context.statePath());
        Instant now = clock.instant();

        GovernorDecision streakDecision = streakAndCooldownDecision(context.statePath(), context.incidentLogPath(), state, now, CyclePhase.TRAINING);
        if (streakDecision != null) {
            return streakDecision;
        }

        if (context.datasetDelta() > policy.maxDatasetDelta()) {
            return halt(context.statePath(), context.incidentLogPath(), CyclePhase.TRAINING, RootCauseCategory.CHANGE_SIZE_EXCEEDED,
                    "Dataset delta exceeded allowed cap", "Review upstream data filtering and reduce training batch size");
        }
        if (context.promptTemplateDiffSize() > policy.maxPromptTemplateDiffSize()) {
            return halt(context.statePath(), context.incidentLogPath(), CyclePhase.TRAINING, RootCauseCategory.CHANGE_SIZE_EXCEEDED,
                    "Prompt/template diff size exceeded allowed cap", "Split prompt/template changes into smaller revisions");
        }
        if (context.confidenceMargin() < policy.minConfidenceMargin()) {
            return halt(context.statePath(), context.incidentLogPath(), CyclePhase.TRAINING, RootCauseCategory.CONFIDENCE_MARGIN_TOO_LOW,
                    "Confidence margin below minimum threshold", "Collect additional validation data and re-run evaluation");
        }

        if (context.uncertainImprovement()) {
            return GovernorDecision.quarantined(policy.quarantineBranch(), "Uncertain improvement routed to quarantine branch");
        }

        return GovernorDecision.pass("All safety checks passed");
    }

    public GovernorDecision evaluatePublishCycle(PublishCycleContext context) throws IOException {
        Objects.requireNonNull(context, "context");
        GovernorState state = loadState(context.statePath());
        Instant now = clock.instant();

        GovernorDecision streakDecision = streakAndCooldownDecision(context.statePath(), context.incidentLogPath(), state, now, CyclePhase.PUBLISH);
        if (streakDecision != null) {
            return streakDecision;
        }

        if (context.datasetDelta() > policy.maxDatasetDelta() || context.promptTemplateDiffSize() > policy.maxPromptTemplateDiffSize()) {
            return halt(context.statePath(), context.incidentLogPath(), CyclePhase.PUBLISH, RootCauseCategory.CHANGE_SIZE_EXCEEDED,
                    "Generated change exceeds configured caps", "Reduce generated artifact delta and retry publish");
        }
        if (context.confidenceMargin() < policy.minConfidenceMargin()) {
            return halt(context.statePath(), context.incidentLogPath(), CyclePhase.PUBLISH, RootCauseCategory.CONFIDENCE_MARGIN_TOO_LOW,
                    "Promotion confidence margin too low", "Run additional regression/safety evaluations before publishing");
        }
        if (context.uncertainImprovement()) {
            return GovernorDecision.quarantined(policy.quarantineBranch(), "Uncertain publish candidate routed to quarantine branch");
        }

        return GovernorDecision.pass("All safety checks passed");
    }

    public void recordOutcome(Path statePath, boolean success, boolean rollbackOccurred) throws IOException {
        GovernorState state = loadState(statePath);
        if (success) {
            state = new GovernorState(0, state.lastRollbackAtEpochMs());
        } else {
            state = new GovernorState(state.consecutiveFailedCycles() + 1, state.lastRollbackAtEpochMs());
        }
        if (rollbackOccurred) {
            state = new GovernorState(state.consecutiveFailedCycles(), clock.instant().toEpochMilli());
        }
        saveState(statePath, state);
    }

    private GovernorDecision streakAndCooldownDecision(Path statePath, Path incidentPath, GovernorState state, Instant now, CyclePhase phase) throws IOException {
        if (state.consecutiveFailedCycles() >= policy.maxConsecutiveFailedCycles()) {
            return halt(statePath, incidentPath, phase, RootCauseCategory.FAILURE_STREAK_EXCEEDED,
                    "Maximum consecutive failed cycles reached", "Investigate last failed runs and reset governor state after remediation");
        }
        if (state.lastRollbackAtEpochMs() > 0) {
            Instant resumeAt = Instant.ofEpochMilli(state.lastRollbackAtEpochMs()).plus(policy.rollbackCooldown());
            if (resumeAt.isAfter(now)) {
                return halt(statePath, incidentPath, phase, RootCauseCategory.ROLLBACK_COOLDOWN_ACTIVE,
                        "Rollback cooldown is active until " + resumeAt, "Wait for cooldown expiry or manually override with operator sign-off");
            }
        }
        return null;
    }

    private GovernorDecision halt(
            Path statePath,
            Path incidentPath,
            CyclePhase phase,
            RootCauseCategory category,
            String summary,
            String requiredAction) throws IOException {
        String incidentId = "incident-" + clock.instant().toEpochMilli();
        IncidentRecord record = new IncidentRecord(incidentId, clock.instant(), phase, category, summary, requiredAction, true);
        appendIncident(incidentPath, record);
        return GovernorDecision.halt(summary, incidentId);
    }

    private GovernorState loadState(Path path) throws IOException {
        if (path == null || !Files.exists(path) || Files.size(path) == 0L) {
            return new GovernorState(0, -1);
        }
        return mapper.readValue(path.toFile(), GovernorState.class);
    }

    private void saveState(Path path, GovernorState state) throws IOException {
        if (path == null) {
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), state);
    }

    private void appendIncident(Path path, IncidentRecord record) throws IOException {
        if (path == null) {
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        String json = mapper.writeValueAsString(record) + System.lineSeparator();
        Files.writeString(path, json, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    public record GovernorPolicy(
            int maxConsecutiveFailedCycles,
            double minConfidenceMargin,
            int maxDatasetDelta,
            int maxPromptTemplateDiffSize,
            Duration rollbackCooldown,
            String quarantineBranch) {
    }

    public record TrainingCycleContext(
            Path statePath,
            Path incidentLogPath,
            int datasetDelta,
            int promptTemplateDiffSize,
            double confidenceMargin,
            boolean uncertainImprovement) {
    }

    public record PublishCycleContext(
            Path statePath,
            Path incidentLogPath,
            int datasetDelta,
            int promptTemplateDiffSize,
            double confidenceMargin,
            boolean uncertainImprovement) {
    }

    public record GovernorDecision(
            boolean continueRunning,
            boolean halted,
            boolean quarantine,
            String branch,
            String reason,
            String incidentId) {
        public static GovernorDecision pass(String reason) {
            return new GovernorDecision(true, false, false, "", reason, "");
        }

        public static GovernorDecision halt(String reason, String incidentId) {
            return new GovernorDecision(false, true, false, "", reason, incidentId);
        }

        public static GovernorDecision quarantined(String branch, String reason) {
            return new GovernorDecision(true, false, true, branch, reason, "");
        }
    }

    public record GovernorState(int consecutiveFailedCycles, long lastRollbackAtEpochMs) {
    }

    public record IncidentRecord(
            String incidentId,
            Instant occurredAt,
            CyclePhase phase,
            RootCauseCategory rootCauseCategory,
            String summary,
            String requiredOperatorAction,
            boolean halted) {
    }

    public enum RootCauseCategory {
        FAILURE_STREAK_EXCEEDED,
        CONFIDENCE_MARGIN_TOO_LOW,
        CHANGE_SIZE_EXCEEDED,
        ROLLBACK_COOLDOWN_ACTIVE
    }

    public enum CyclePhase {
        TRAINING,
        PUBLISH
    }
}
