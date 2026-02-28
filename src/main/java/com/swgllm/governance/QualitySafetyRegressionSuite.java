package com.swgllm.governance;

import java.util.ArrayList;
import java.util.List;

public class QualitySafetyRegressionSuite {

    public GovernanceEvaluation evaluate(GovernanceMetrics metrics, GovernancePolicy policy) {
        return evaluate(metrics, policy, List.of());
    }

    public GovernanceEvaluation evaluate(
            GovernanceMetrics metrics,
            GovernancePolicy policy,
            List<GovernanceTestResult> testResults) {
        List<String> failures = new ArrayList<>();
        if (metrics.hallucinationRate() > policy.maxHallucinationRate()) {
            failures.add(String.format(
                    "Hallucination rate threshold exceeded (actual=%.4f, max=%.4f, delta=+%.4f)",
                    metrics.hallucinationRate(),
                    policy.maxHallucinationRate(),
                    metrics.hallucinationRate() - policy.maxHallucinationRate()));
        }
        if (metrics.unsafeOutputRate() > policy.maxUnsafeOutputRate()) {
            failures.add(String.format(
                    "Unsafe output threshold exceeded (actual=%.4f, max=%.4f, delta=+%.4f)",
                    metrics.unsafeOutputRate(),
                    policy.maxUnsafeOutputRate(),
                    metrics.unsafeOutputRate() - policy.maxUnsafeOutputRate()));
        }
        if (metrics.swgDomainAccuracy() < policy.minSwgDomainAccuracy()) {
            failures.add(String.format(
                    "SWG-domain accuracy benchmark below minimum (actual=%.4f, min=%.4f, delta=-%.4f)",
                    metrics.swgDomainAccuracy(),
                    policy.minSwgDomainAccuracy(),
                    policy.minSwgDomainAccuracy() - metrics.swgDomainAccuracy()));
        }

        testResults.stream()
                .filter(result -> !result.failureReasons().isEmpty())
                .forEach(result -> failures.add("Test " + result.testId() + " failed: " + String.join("; ", result.failureReasons())));

        return new GovernanceEvaluation(failures.isEmpty(), failures);
    }
}
