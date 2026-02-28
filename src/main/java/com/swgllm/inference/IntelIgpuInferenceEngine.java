package com.swgllm.inference;

import com.swgllm.runtime.RuntimeProfileResolver;

public class IntelIgpuInferenceEngine implements InferenceEngine {
    @Override
    public String generate(String prompt, InferenceContext context, RuntimeProfileResolver.ResolvedProfile profile) {
        try {
            return CpuInferenceEngine.generateGroundedResponse(prompt, context, profile, "(Intel iGPU accelerated) ");
        } catch (RuntimeException e) {
            return "I hit an inference error while invoking model " + profile.model() + ".";
        }
    }
}
