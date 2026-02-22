package com.swgllm.pipeline;

public record TrainingExample(String prompt, String preferredResponse, String correctedResponse) {
}
