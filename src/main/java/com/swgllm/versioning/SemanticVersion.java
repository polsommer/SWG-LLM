package com.swgllm.versioning;

public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {
    public static SemanticVersion parse(String value) {
        String[] parts = value.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Version must follow semantic version format x.y.z");
        }
        return new SemanticVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    @Override
    public int compareTo(SemanticVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
