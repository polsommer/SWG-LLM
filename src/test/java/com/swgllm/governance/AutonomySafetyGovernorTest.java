package com.swgllm.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomySafetyGovernorTest {

    @Test
    void shouldHaltWhenFailureStreakReachedAndEmitIncident() throws Exception {
        Path tempDir = Files.createTempDirectory("governor");
        Path statePath = tempDir.resolve("state.json");
        Path incidentPath = tempDir.resolve("incidents.jsonl");

        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        AutonomySafetyGovernor governor = new AutonomySafetyGovernor(
                new AutonomySafetyGovernor.GovernorPolicy(2, 0.02, 50, 20, Duration.ofMinutes(30), "quarantine"),
                clock);

        governor.recordOutcome(statePath, false, false);
        governor.recordOutcome(statePath, false, false);

        AutonomySafetyGovernor.GovernorDecision decision = governor.evaluateTrainingCycle(
                new AutonomySafetyGovernor.TrainingCycleContext(statePath, incidentPath, 2, 1, 0.2, false));

        assertTrue(decision.halted());
        assertTrue(Files.exists(incidentPath));
        assertFalse(Files.readString(incidentPath).isBlank());
    }

    @Test
    void shouldRouteUncertainImprovementToQuarantine() throws Exception {
        Path tempDir = Files.createTempDirectory("governor-quarantine");
        AutonomySafetyGovernor governor = new AutonomySafetyGovernor(
                new AutonomySafetyGovernor.GovernorPolicy(3, 0.02, 50, 20, Duration.ofMinutes(30), "quarantine/branch"),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        AutonomySafetyGovernor.GovernorDecision decision = governor.evaluatePublishCycle(
                new AutonomySafetyGovernor.PublishCycleContext(
                        tempDir.resolve("state.json"),
                        tempDir.resolve("incidents.jsonl"),
                        1,
                        1,
                        0.03,
                        true));

        assertTrue(decision.quarantine());
        assertFalse(decision.halted());
    }
}
