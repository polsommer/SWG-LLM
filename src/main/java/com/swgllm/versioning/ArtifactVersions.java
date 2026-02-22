package com.swgllm.versioning;

public record ArtifactVersions(
        SemanticVersion promptTemplateVersion,
        SemanticVersion retrieverVersion,
        SemanticVersion modelVersion) {
}
