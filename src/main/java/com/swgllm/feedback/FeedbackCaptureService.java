package com.swgllm.feedback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class FeedbackCaptureService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<![\\w+])(?:\\+?\\d[\\d()\\- ]{6,}\\d)(?!\\w)");
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    public FeedbackRecord capture(
            Path feedbackLogPath,
            FeedbackRating rating,
            String prompt,
            String response,
            String correctedAnswer,
            boolean approvedForTraining) throws IOException {
        FeedbackRecord record = new FeedbackRecord(
                UUID.randomUUID().toString(),
                rating,
                anonymize(prompt),
                anonymize(response),
                correctedAnswer == null ? "" : anonymize(correctedAnswer),
                approvedForTraining,
                Instant.now());
        List<FeedbackRecord> records = load(feedbackLogPath);
        records.add(record);
        save(feedbackLogPath, records);
        return record;
    }

    public List<FeedbackRecord> load(Path feedbackLogPath) throws IOException {
        if (!Files.exists(feedbackLogPath)) {
            return new ArrayList<>();
        }
        if (Files.size(feedbackLogPath) == 0L) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(feedbackLogPath.toFile(), new TypeReference<List<FeedbackRecord>>() {
        });
    }

    private void save(Path feedbackLogPath, List<FeedbackRecord> records) throws IOException {
        if (feedbackLogPath.getParent() != null) {
            Files.createDirectories(feedbackLogPath.getParent());
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(feedbackLogPath.toFile(), records);
    }

    public String anonymize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String hiddenEmails = EMAIL_PATTERN.matcher(value).replaceAll("[REDACTED_EMAIL]");
        return PHONE_PATTERN.matcher(hiddenEmails).replaceAll("[REDACTED_PHONE]");
    }
}
