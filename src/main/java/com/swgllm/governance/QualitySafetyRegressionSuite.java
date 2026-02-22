package com.swgllm.governance;

import java.util.ArrayList;
import java.util.List;

public class QualitySafetyRegressionSuite {

    public GovernanceEvaluation evaluate(GovernanceMetrics metrics, GovernancePolicy policy) {
        List<String> failures = new ArrayList<>();
        if (metrics.hallucinationRate() > policy.maxHallucinationRate()) {
            failures.add("Hallucination rate threshold exceeded");
        }
        if (metrics.unsafeOutputRate() > policy.maxUnsafeOutputRate()) {
            failures.add("Unsafe output threshold exceeded");
        }
        if (metrics.swgDomainAccuracy() < policy.minSwgDomainAccuracy()) {
            failures.add("SWG-domain accuracy benchmark below minimum");
        }
        return new GovernanceEvaluation(failures.isEmpty(), failures);
    }
}
