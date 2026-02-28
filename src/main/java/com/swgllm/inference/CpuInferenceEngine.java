package com.swgllm.inference;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.swgllm.ingest.SearchResult;
import com.swgllm.runtime.RuntimeProfileResolver;

public class CpuInferenceEngine implements InferenceEngine {
    @Override
    public String generate(String prompt, InferenceContext context, RuntimeProfileResolver.ResolvedProfile profile) {
        try {
            return generateGroundedResponse(prompt, context, profile, "");
        } catch (RuntimeException e) {
            return "I hit an inference error while invoking model " + profile.model() + ".";
        }
    }

    static String generateGroundedResponse(
            String prompt,
            InferenceContext context,
            RuntimeProfileResolver.ResolvedProfile profile,
            String enginePrefix) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be blank");
        }

        String userRequest = extractCurrentUserRequest(prompt);
        String requestKeywords = keywords(userRequest);

        String snippetsSummary;
        if (context.retrievedSnippets().isEmpty()) {
            snippetsSummary = "No snippets available; ingestion may be required.";
        } else {
            snippetsSummary = context.retrievedSnippets().stream()
                    .map(result -> toSnippetSummary(result, requestKeywords))
                    .collect(Collectors.joining(" | "));
        }

        String response = enginePrefix
                + "Model=" + profile.model()
                + " backend=" + profile.backend()
                + " temp=" + profile.temperature()
                + " top_p=" + profile.topP()
                + " max_tokens=" + profile.maxTokens()
                + "\nRequest: " + userRequest
                + "\nGrounded evidence: " + snippetsSummary;

        return truncateByTokens(response, profile.maxTokens());
    }

    private static String extractCurrentUserRequest(String prompt) {
        String marker = "Current user request:";
        int markerIndex = prompt.lastIndexOf(marker);
        if (markerIndex < 0) {
            return prompt.trim();
        }
        return prompt.substring(markerIndex + marker.length()).trim();
    }

    private static String keywords(String input) {
        Set<String> words = Arrays.stream(input.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() > 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return String.join(" ", words);
    }

    private static String toSnippetSummary(SearchResult result, String requestKeywords) {
        String text = result.chunk().text();
        String selected = firstMatchingSentence(text, requestKeywords);
        return result.citationSnippet() + " => " + selected;
    }

    private static String firstMatchingSentence(String text, String keywords) {
        if (text == null || text.isBlank()) {
            return "(empty snippet)";
        }
        String[] sentences = text.split("(?<=[.!?])\\s+");
        if (keywords != null && !keywords.isBlank()) {
            Set<String> keywordSet = Arrays.stream(keywords.split("\\s+"))
                    .collect(Collectors.toSet());
            for (String sentence : sentences) {
                String lower = sentence.toLowerCase(Locale.ROOT);
                if (keywordSet.stream().anyMatch(lower::contains)) {
                    return sentence.trim();
                }
            }
        }
        return sentences[0].trim();
    }

    private static String truncateByTokens(String text, int maxTokens) {
        if (maxTokens <= 0) {
            return text;
        }
        String[] tokens = text.split("\\s+");
        if (tokens.length <= maxTokens) {
            return text;
        }
        return String.join(" ", Arrays.copyOf(tokens, maxTokens));
    }
}
