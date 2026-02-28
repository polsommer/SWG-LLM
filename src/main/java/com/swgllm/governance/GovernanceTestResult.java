package com.swgllm.governance;

import java.util.List;

public record GovernanceTestResult(
        String testId,
        String prompt,
        String output,
        boolean expectedMatch,
        boolean citationMatch,
        boolean unsafe,
        List<String> failureReasons) {
}
