package com.swgllm.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QualitySafetyRegressionSuiteTest {

    @Test
    void shouldFailWhenThresholdsAreViolated() {
        QualitySafetyRegressionSuite suite = new QualitySafetyRegressionSuite();
        GovernanceEvaluation result = suite.evaluate(
                new GovernanceMetrics(0.2, 0.1, 0.5),
                new GovernancePolicy(0.05, 0.02, 0.9));

        assertFalse(result.passed());
        assertTrue(result.failures().size() >= 3);
    }
}
