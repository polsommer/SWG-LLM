package com.swgllm.feedback;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackCaptureServiceTest {

    @Test
    void shouldCaptureAnonymizedFeedback() throws Exception {
        Path temp = Files.createTempFile("feedback", ".json");
        FeedbackCaptureService service = new FeedbackCaptureService();

        FeedbackRecord record = service.capture(
                temp,
                FeedbackRating.THUMBS_DOWN,
                "email me at test@example.com",
                "call +1 (555) 123-9999",
                "Use test@example.com",
                true);

        assertTrue(record.anonymizedPrompt().contains("[REDACTED_EMAIL]"));
        assertTrue(record.anonymizedResponse().contains("[REDACTED_PHONE]"));
        assertTrue(record.correctedAnswer().contains("[REDACTED_EMAIL]"));
        assertEquals(1, service.load(temp).size());
    }
}
