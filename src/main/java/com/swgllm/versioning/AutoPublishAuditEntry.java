package com.swgllm.versioning;

import java.time.Instant;
import java.util.Map;

public record AutoPublishAuditEntry(
        Instant timestamp,
        String actor,
        String reason,
        String repoUrl,
        String branch,
        boolean dryRun,
        boolean governancePassed,
        boolean evaluationPassed,
        boolean highImpactChange,
        boolean manualApprovalProvided,
        double scoreDelta,
        Map<String, Double> metrics,
        String outcome,
        String details,
        String commitMessage,
        String commitHash) {
}
