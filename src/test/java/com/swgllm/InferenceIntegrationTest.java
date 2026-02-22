package com.swgllm;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.swgllm.ingest.ChunkMetadata;
import com.swgllm.ingest.DocumentChunk;
import com.swgllm.ingest.SearchResult;
import com.swgllm.inference.CpuInferenceEngine;
import com.swgllm.inference.InferenceEngine;
import com.swgllm.inference.IntelIgpuInferenceEngine;
import com.swgllm.runtime.RuntimeProfileResolver;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InferenceIntegrationTest {

    @Test
    void shouldSelectIntelIgpuEngineWhenBackendIsIntel() {
        RuntimeProfileResolver.ResolvedProfile profile = new RuntimeProfileResolver.ResolvedProfile(
                "intel-igpu",
                "intel-igpu",
                "test-model",
                "intel-igpu",
                1024,
                3,
                "");

        InferenceEngine engine = Main.selectInferenceEngine(profile);

        assertInstanceOf(IntelIgpuInferenceEngine.class, engine);
    }

    @Test
    void shouldSelectCpuEngineWhenBackendIsCpu() {
        RuntimeProfileResolver.ResolvedProfile profile = new RuntimeProfileResolver.ResolvedProfile(
                "cpu-low-memory",
                "cpu-low-memory",
                "test-model",
                "cpu",
                1024,
                3,
                "");

        InferenceEngine engine = Main.selectInferenceEngine(profile);

        assertInstanceOf(CpuInferenceEngine.class, engine);
    }

    @Test
    void shouldBuildPromptWithSystemHistoryAndSnippets() {
        RuntimeProfileResolver.ResolvedProfile profile = new RuntimeProfileResolver.ResolvedProfile(
                "cpu-low-memory",
                "cpu-low-memory",
                "model-a",
                "cpu",
                2048,
                4,
                "");
        List<String> conversation = List.of(
                "user: hi",
                "assistant: hello");
        SearchResult searchResult = new SearchResult(
                new DocumentChunk(
                        "chunk-1",
                        "snippet body",
                        new ChunkMetadata("src/A.java", List.of("A#method"), "abc", "v1", 0, 1, 10)),
                0.8f,
                0.7f,
                "src/A.java:1-10");

        String prompt = Main.buildPrompt("How does this work?", conversation, List.of(searchResult), profile);

        assertTrue(prompt.contains("System instruction:"));
        assertTrue(prompt.contains("Conversation history:"));
        assertTrue(prompt.contains("user: hi"));
        assertTrue(prompt.contains("assistant: hello"));
        assertTrue(prompt.contains("Retrieved snippets:"));
        assertTrue(prompt.contains("[1] src/A.java:1-10"));
        assertTrue(prompt.contains("Current user request:\nHow does this work?"));
    }
}
