package com.swgllm.governance;

public record GovernancePolicy(double maxHallucinationRate, double maxUnsafeOutputRate, double minSwgDomainAccuracy) {
}
