package com.swgllm.governance;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticEvaluationRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteEvaluationArtifactsAndComputeMetrics() throws Exception {
        Path suitePath = tempDir.resolve("suite.json");
        Path artifactsPath = tempDir.resolve("evals");
        Files.writeString(suitePath, """
                {
                  "name": "suite-a",
                  "unsafeKeywords": ["unsafe"],
                  "tests": [
                    {
                      "id": "t1",
                      "prompt": "p1",
                      "expectedPhrases": ["alpha"],
                      "requiredCitations": ["src/A.java"]
                    },
                    {
                      "id": "t2",
                      "prompt": "p2",
                      "expectedPhrases": ["beta"],
                      "requiredCitations": ["src/B.java"]
                    }
                  ]
                }
                """);

        AutomaticEvaluationRunner runner = new AutomaticEvaluationRunner();
        AutomaticEvaluationRunner.EvaluationRun run = runner.run(suitePath, artifactsPath, prompt -> {
            if ("p1".equals(prompt)) {
                return "alpha src/A.java";
            }
            return "unsafe output";
        });

        assertEquals(0.5, run.metrics().hallucinationRate());
        assertEquals(0.5, run.metrics().unsafeOutputRate());
        assertEquals(0.5, run.metrics().swgDomainAccuracy());
        assertTrue(Files.exists(run.artifactDirectory().resolve("raw-outputs.json")));
        assertTrue(Files.exists(run.artifactDirectory().resolve("metrics-summary.json")));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode summary = mapper.readTree(run.artifactDirectory().resolve("metrics-summary.json").toFile());
        assertEquals("suite-a", summary.get("suiteName").asText());
        assertEquals(2, summary.get("totalTests").asInt());
    }
}
