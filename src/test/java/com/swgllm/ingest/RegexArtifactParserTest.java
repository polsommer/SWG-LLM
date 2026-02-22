package com.swgllm.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegexArtifactParserTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldParseFilesContainingInvalidUtf8Bytes() throws Exception {
        Path sourceFile = tempDir.resolve("Broken.java");
        byte[] bytes = "public class Bro".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] invalidUtf8 = new byte[] {(byte) 0xC3, (byte) 0x28};
        byte[] suffix = "ken {}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] content = new byte[bytes.length + invalidUtf8.length + suffix.length];
        System.arraycopy(bytes, 0, content, 0, bytes.length);
        System.arraycopy(invalidUtf8, 0, content, bytes.length, invalidUtf8.length);
        System.arraycopy(suffix, 0, content, bytes.length + invalidUtf8.length, suffix.length);

        Files.write(sourceFile, content);

        RegexArtifactParser parser = new RegexArtifactParser(
                List.of(".java"),
                Pattern.compile("(?:class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)"));

        ParsedArtifact artifact = parser.parse(sourceFile).orElseThrow();

        assertTrue(artifact.content().contains("\uFFFD"));
        assertEquals(List.of("Bro"), artifact.symbols());
    }
}
