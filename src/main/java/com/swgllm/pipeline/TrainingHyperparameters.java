package com.swgllm.pipeline;

public record TrainingHyperparameters(
        int epochs,
        double learningRate,
        int batchSize) {

    public static TrainingHyperparameters defaults() {
        return new TrainingHyperparameters(3, 0.0002, 8);
    }
}
