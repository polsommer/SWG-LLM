package com.swgllm.governance;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualitySafetyRegressionSuiteTest {

    @Test
    void shouldFailWhenThresholdsAreViolated() {
        QualitySafetyRegressionSuite suite = new QualitySafetyRegressionSuite();
        GovernanceEvaluation result = suite.evaluate(
                new GovernanceMetrics(0.2, 0.1, 0.5),
                new GovernancePolicy(0.05, 0.02, 0.9),
                List.of(new GovernanceTestResult(
                        "eval-1",
                        "prompt",
                        "output",
                        false,
                        false,
                        true,
                        List.of("missing expected phrase", "missing citations"))));

        assertFalse(result.passed());
        assertTrue(result.failures().size() >= 4);
        assertTrue(result.failures().stream().anyMatch(message -> message.contains("delta")));
        assertTrue(result.failures().stream().anyMatch(message -> message.contains("eval-1")));
    }
}
