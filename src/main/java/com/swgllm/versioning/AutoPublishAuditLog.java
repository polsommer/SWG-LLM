package com.swgllm.versioning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class AutoPublishAuditLog {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

    public void append(Path auditLogPath, AutoPublishAuditEntry entry) throws IOException {
        if (auditLogPath.getParent() != null) {
            Files.createDirectories(auditLogPath.getParent());
        }
        String line = mapper.writeValueAsString(entry) + System.lineSeparator();
        Files.writeString(auditLogPath, line, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }

    public long countSuccessfulPushesSince(Path auditLogPath, Instant cutoffInclusive) throws IOException {
        return readAll(auditLogPath).stream()
                .filter(entry -> "PUSHED".equals(entry.outcome()))
                .filter(entry -> !entry.timestamp().isBefore(cutoffInclusive))
                .count();
    }

    public List<AutoPublishAuditEntry> readAll(Path auditLogPath) throws IOException {
        if (!Files.exists(auditLogPath)) {
            return List.of();
        }
        List<String> lines = Files.readAllLines(auditLogPath);
        List<AutoPublishAuditEntry> entries = new ArrayList<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            entries.add(mapper.readValue(line, AutoPublishAuditEntry.class));
        }
        return entries;
    }
}
