package com.swgllm.governance;

public record GovernanceMetrics(double hallucinationRate, double unsafeOutputRate, double swgDomainAccuracy) {
}
