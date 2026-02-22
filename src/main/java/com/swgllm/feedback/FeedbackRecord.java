package com.swgllm.feedback;

import java.time.Instant;

public record FeedbackRecord(
        String requestId,
        FeedbackRating rating,
        String anonymizedPrompt,
        String anonymizedResponse,
        String correctedAnswer,
        boolean approvedForTraining,
        Instant createdAt) {
}
