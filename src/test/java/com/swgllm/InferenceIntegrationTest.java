package com.swgllm;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.swgllm.ingest.ChunkMetadata;
import com.swgllm.ingest.DocumentChunk;
import com.swgllm.ingest.SearchResult;
import com.swgllm.inference.CpuInferenceEngine;
import com.swgllm.inference.InferenceContext;
import com.swgllm.inference.InferenceEngine;
import com.swgllm.inference.IntelIgpuInferenceEngine;
import com.swgllm.runtime.RuntimeProfileResolver;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
                0.0,
                1.0,
                256,
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
                0.0,
                1.0,
                256,
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
                0.0,
                1.0,
                256,
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
        assertTrue(prompt.contains("Behavior rules:"));
        assertTrue(prompt.contains("If user intent is ambiguous, ask one brief clarifying question"));
        assertTrue(prompt.contains("Prefer short, direct answers first"));
        assertTrue(prompt.contains("include citations like [1], [2]"));
        assertTrue(prompt.contains("explicitly state uncertainty"));
        assertTrue(prompt.contains("Do not dump raw snippets verbatim unless the user explicitly asks"));
        assertTrue(prompt.contains("Current user request:\nHow does this work?"));
    }

    @Test
    void shouldGeneratePromptSpecificResponseUsingMultipleSnippets() {
        RuntimeProfileResolver.ResolvedProfile profile = new RuntimeProfileResolver.ResolvedProfile(
                "cpu-low-memory",
                "cpu-low-memory",
                "model-a",
                "cpu",
                2048,
                4,
                0.0,
                1.0,
                256,
                "");

        List<SearchResult> sources = List.of(
                searchResult("chunk-1", "src/Auth.java", 1, 12, "Auth requires MFA and role checks before login."),
                searchResult("chunk-2", "src/Session.java", 20, 38, "Session tokens expire every 15 minutes and can be refreshed."));

        String promptA = Main.buildPrompt("How does MFA login work?", List.of(), sources, profile);
        String promptB = Main.buildPrompt("When do session tokens expire?", List.of(), sources, profile);

        CpuInferenceEngine engine = new CpuInferenceEngine();
        String responseA = engine.generate(promptA, new InferenceContext(List.of(), sources), profile);
        String responseB = engine.generate(promptB, new InferenceContext(List.of(), sources), profile);

        assertNotEquals(responseA, responseB);
        assertTrue(responseA.contains("temp=0.0") && responseA.contains("top_p=1.0") && responseA.contains("max_tokens=256"));
        assertTrue(responseA.contains("src/Auth.java:1-12"));
        assertTrue(responseA.contains("src/Session.java:20-38"));
        assertTrue(responseB.contains("session tokens expire"));
    }

    private static SearchResult searchResult(String chunkId, String filePath, int lineStart, int lineEnd, String text) {
        return new SearchResult(
                new DocumentChunk(
                        chunkId,
                        text,
                        new ChunkMetadata(filePath, List.of("symbol"), "abc", "v1", 0, lineStart, lineEnd)),
                0.8f,
                0.7f,
                filePath + ":" + lineStart + "-" + lineEnd);
    }
}
