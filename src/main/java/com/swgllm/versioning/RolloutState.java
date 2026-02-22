package com.swgllm.versioning;

public record RolloutState(ArtifactVersions previousStable, ArtifactVersions currentStable, ArtifactVersions currentCanary) {
    public static RolloutState initial() {
        ArtifactVersions baseline = new ArtifactVersions(
                new SemanticVersion(0, 1, 0),
                new SemanticVersion(0, 1, 0),
                new SemanticVersion(0, 1, 0));
        return new RolloutState(baseline, baseline, baseline);
    }
}
