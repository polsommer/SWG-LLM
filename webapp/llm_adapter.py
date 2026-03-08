"""LLM adapter utilities for retrieval-augmented chat."""

from __future__ import annotations

import json
import os
import re
from typing import Any
from urllib import error, request

from ingestion.knowledge_store import QueryResult
from ingestion.query_interface import KnowledgeQueryService


_DEFAULT_ABSTAIN_ANSWER = (
    "There is not enough evidence in the retrieved documents to answer confidently. "
    "Please provide more context or ingest more relevant sources."
)

_FOLLOW_UP_STARTERS = {
    "it",
    "they",
    "that",
    "those",
    "this",
    "he",
    "she",
    "these",
    "there",
}


def rewrite_question(question: str, history: list[str] | None = None) -> str:
    """Expand follow-up questions with recent context for better retrieval recall."""
    normalized = " ".join(question.split())
    if not normalized:
        return question

    prior_turns = [" ".join(item.split()) for item in (history or []) if item.strip()]
    if not prior_turns:
        return normalized

    tokens = [re.sub(r"[^a-zA-Z0-9_]", "", token).lower() for token in normalized.split()]
    follow_up = any(token in _FOLLOW_UP_STARTERS for token in tokens)
    if not follow_up and len(tokens) <= 3:
        follow_up = True
    if not follow_up:
        return normalized

    context_window = " | ".join(prior_turns[-2:])
    return f"{normalized} (context: {context_window})"


def build_context(
    question: str,
    top_k: int,
    service: KnowledgeQueryService | None = None,
) -> list[QueryResult]:
    """Retrieve top-k evidence chunks for a user question."""
    query_service = service or KnowledgeQueryService()
    return query_service.query(question, top_k=max(1, top_k))


def build_prompt(question: str, docs: list[QueryResult]) -> str:
    """Build a grounded prompt from retrieval results."""
    if not docs:
        return (
            "You must abstain when evidence is missing.\n"
            f"Question: {question}\n"
            "Evidence: <none>\n"
            f"Return exactly: {_DEFAULT_ABSTAIN_ANSWER}"
        )

    evidence_blocks = []
    for doc in docs:
        evidence_blocks.append(
            f"[{doc.file_path}:{doc.start_line}-{doc.end_line}]\n{doc.text}"
        )

    evidence = "\n\n".join(evidence_blocks)
    return (
        "You are a retrieval-grounded assistant aligned to automatically ingested sources. "
        "Answer only from the evidence below. When you make a claim, cite at least one bracketed "
        "reference in this format: [path:start-end]. If evidence is insufficient, explicitly say there "
        "is not enough evidence.\n\n"
        f"Question:\n{question}\n\n"
        f"Evidence:\n{evidence}\n\n"
        "Answer:"
    )


def get_backend_name() -> str:
    """Return the selected backend mode."""
    backend = os.getenv("SWG_LLM_BACKEND", "mock").strip().lower()
    return backend or "mock"


def generate_answer(prompt: str) -> str:
    """Generate an answer through the configured backend."""
    backend = get_backend_name()
    if backend == "openai":
        return _generate_openai(prompt)
    if backend == "ollama":
        return _generate_ollama(prompt)
    return _generate_mock(prompt)


def _generate_mock(prompt: str) -> str:
    if "Evidence: <none>" in prompt:
        return _DEFAULT_ABSTAIN_ANSWER

    lines = [line.strip() for line in prompt.splitlines() if line.strip().startswith("[")]
    if not lines:
        return _DEFAULT_ABSTAIN_ANSWER
    top_refs = ", ".join(lines[:2])
    return f"Based on retrieved evidence {top_refs}, the answer is grounded in the provided context."


def _generate_openai(prompt: str) -> str:
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        return _generate_mock(prompt)

    base_url = os.getenv("SWG_OPENAI_BASE_URL", "https://api.openai.com/v1")
    model = os.getenv("SWG_OPENAI_MODEL", "gpt-4o-mini")
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0,
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    data = _post_json(f"{base_url.rstrip('/')}/chat/completions", payload, headers)
    try:
        return data["choices"][0]["message"]["content"].strip()
    except (KeyError, IndexError, TypeError):
        return _generate_mock(prompt)


def _generate_ollama(prompt: str) -> str:
    base_url = os.getenv("SWG_OLLAMA_BASE_URL")
    if not base_url:
        return _generate_mock(prompt)
    model = os.getenv("SWG_OLLAMA_MODEL", "llama3.1")
    payload = {
        "model": model,
        "prompt": prompt,
        "stream": False,
    }
    data = _post_json(f"{base_url.rstrip('/')}/api/generate", payload, {"Content-Type": "application/json"})
    answer = data.get("response")
    if isinstance(answer, str) and answer.strip():
        return answer.strip()
    return _generate_mock(prompt)


def _post_json(url: str, payload: dict[str, Any], headers: dict[str, str]) -> dict[str, Any]:
    req = request.Request(
        url=url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=30) as response:
            raw = response.read().decode("utf-8")
    except (error.URLError, TimeoutError):
        return {}

    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}
