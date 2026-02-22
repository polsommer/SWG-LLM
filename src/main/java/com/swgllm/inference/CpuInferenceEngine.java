package com.swgllm.inference;

import com.swgllm.ingest.SearchResult;
import com.swgllm.runtime.RuntimeProfileResolver;

public class CpuInferenceEngine implements InferenceEngine {
    @Override
    public String generate(String prompt, InferenceContext context, RuntimeProfileResolver.ResolvedProfile profile) {
        if (context.retrievedSnippets().isEmpty()) {
            return "I could not find indexed sources yet. Run ingest mode first.";
        }
        SearchResult top = context.retrievedSnippets().get(0);
        return "Using model " + profile.model() + " on " + profile.backend()
                + ", here is a grounded response: " + top.chunk().text();
    }
}
