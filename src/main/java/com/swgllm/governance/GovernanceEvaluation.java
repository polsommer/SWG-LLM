package com.swgllm.governance;

import java.util.List;

public record GovernanceEvaluation(boolean passed, List<String> failures) {
}
