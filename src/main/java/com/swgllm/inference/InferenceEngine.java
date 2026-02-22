package com.swgllm.inference;

import com.swgllm.runtime.RuntimeProfileResolver;

public interface InferenceEngine {
    String generate(String prompt, InferenceContext context, RuntimeProfileResolver.ResolvedProfile profile);
}
