package com.swgllm.governance;

import java.util.List;

public class UnsafeOutputFilter {
    private final List<String> blockedTerms;

    public UnsafeOutputFilter(List<String> blockedTerms) {
        this.blockedTerms = blockedTerms;
    }

    public boolean isUnsafe(String output) {
        if (output == null) {
            return false;
        }
        String normalized = output.toLowerCase();
        return blockedTerms.stream().map(String::toLowerCase).anyMatch(normalized::contains);
    }
}
