package com.swgllm.versioning;

/**
 * Central registry for system prompt policy templates so policy updates can be
 * rolled out through versioned prompt-template changes.
 */
public final class PromptPolicyTemplateRegistry {
    private static final String ACTIVE_SYSTEM_POLICY = String.join("\n",
            "You are SWG-LLM. Answer with grounded, concise guidance using provided snippets when relevant.",
            "Behavior rules:",
            "- If user intent is ambiguous, ask one brief clarifying question before answering.",
            "- Prefer short, direct answers first, then add details if useful.",
            "- When using retrieved snippets, include citations like [1], [2] that map to snippet indices.",
            "- If required context is missing, explicitly state uncertainty and what is missing.",
            "- Do not dump raw snippets verbatim unless the user explicitly asks for verbatim text.");

    private PromptPolicyTemplateRegistry() {
    }

    public static String activeSystemPolicy() {
        return ACTIVE_SYSTEM_POLICY;
    }
}
