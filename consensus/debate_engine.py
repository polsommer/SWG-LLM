"""Inter-agent debate orchestration and result persistence."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import UTC, datetime
import json
import os
from pathlib import Path
from typing import Callable

from ingestion.query_interface import QueryResult


@dataclass(frozen=True)
class Evidence:
    """Evidence citation emitted by an agent."""

    file_path: str
    start_line: int
    end_line: int
    score: float
    excerpt: str
    document_title: str = "unknown"
    section: str = "root"
    last_updated: str = "unknown"
    access_scope: str = "restricted"
    source_kind: str = "code"


@dataclass(frozen=True)
class AgentResponse:
    """Response payload produced by an agent in one round."""

    node_id: str
    prompt: str
    answer: str
    evidence: tuple[Evidence, ...]
    round_index: int = 0


@dataclass(frozen=True)
class MergedConclusion:
    """Persisted consensus artifact for downstream sync."""

    prompt: str
    conclusion: str
    confidence: float
    agreement_score: float
    provenance: dict[str, tuple[Evidence, ...]]
    rounds_used: int
    grounded: bool


AnswerGenerator = Callable[[str, tuple[Evidence, ...], tuple[Evidence, ...], int], str]
Retriever = Callable[[str, int], list[dict[str, object]]]


class DebateEngine:
    """Runs independent responses from both nodes using shared ingested knowledge."""

    NODE_A = "192.168.88.5"
    NODE_B = "192.168.88.10"

    def __init__(
        self,
        retrieve_a: Retriever,
        retrieve_b: Retriever,
        answer_a: AnswerGenerator | None = None,
        answer_b: AnswerGenerator | None = None,
        workdir: Path | None = None,
    ) -> None:
        self._retrieve_a = retrieve_a
        self._retrieve_b = retrieve_b
        self._answer_a = answer_a or self._default_answer
        self._answer_b = answer_b or self._default_answer
        self.workdir = (workdir or Path(os.getenv("SWG_WORKDIR", ".swg")) / "consensus").resolve()
        self.workdir.mkdir(parents=True, exist_ok=True)
        self.retrieval_log_file = self.workdir / "retrieval_usage.jsonl"

    def initial_responses(self, prompt: str, top_k: int = 5) -> tuple[AgentResponse, AgentResponse]:
        evidence_a = self._fetch_evidence(self._retrieve_a, prompt, top_k)
        evidence_b = self._fetch_evidence(self._retrieve_b, prompt, top_k)

        response_a = AgentResponse(
            node_id=self.NODE_A,
            prompt=prompt,
            answer=self._answer_a(prompt, evidence_a, evidence_b, 0),
            evidence=evidence_a,
            round_index=0,
        )
        response_b = AgentResponse(
            node_id=self.NODE_B,
            prompt=prompt,
            answer=self._answer_b(prompt, evidence_b, evidence_a, 0),
            evidence=evidence_b,
            round_index=0,
        )
        return response_a, response_b

    def revised_response(
        self,
        node_id: str,
        prompt: str,
        own_evidence: tuple[Evidence, ...],
        peer_evidence: tuple[Evidence, ...],
        round_index: int,
    ) -> AgentResponse:
        generator = self._answer_a if node_id == self.NODE_A else self._answer_b
        return AgentResponse(
            node_id=node_id,
            prompt=prompt,
            answer=generator(prompt, own_evidence, peer_evidence, round_index),
            evidence=own_evidence,
            round_index=round_index,
        )

    def merge_conclusions(
        self,
        prompt: str,
        response_a: AgentResponse,
        response_b: AgentResponse,
        agreement_score: float,
        rounds_used: int,
        token_budget: int = 700,
    ) -> MergedConclusion:
        winner = response_a if len(response_a.answer) >= len(response_b.answer) else response_b
        retrieved_context = self._build_prompt_context(
            prompt=prompt,
            evidence=winner.evidence,
            token_budget=token_budget,
        )
        grounded = bool(retrieved_context["chunks"])

        if grounded:
            chunk_summary = "\n".join(
                f"- [{item['citation']}] {item['text']}" for item in retrieved_context["chunks"]
            )
            conclusion = (
                f"Grounded answer for '{prompt}':\n"
                f"{winner.answer}\n\n"
                f"Retrieved evidence:\n{chunk_summary}\n\n"
                "Policy: answer is grounded only in retrieved evidence."
            )
        else:
            conclusion = (
                f"Insufficient grounded evidence for '{prompt}'. "
                "The model should abstain instead of answering without citations."
            )

        confidence = max(0.0, min(1.0, agreement_score * 0.75 + (0.25 if grounded else 0.05)))

        self._log_grounding(
            prompt=prompt,
            grounded=grounded,
            chunks_used=len(retrieved_context["chunks"]),
            token_budget=token_budget,
        )

        return MergedConclusion(
            prompt=prompt,
            conclusion=conclusion,
            confidence=confidence,
            agreement_score=agreement_score,
            provenance={
                response_a.node_id: response_a.evidence,
                response_b.node_id: response_b.evidence,
            },
            rounds_used=rounds_used,
            grounded=grounded,
        )

    def persist_conclusion(self, conclusion: MergedConclusion, filename: str | None = None) -> Path:
        artifact_name = filename or "merged_conclusion.json"
        path = self.workdir / artifact_name

        payload = asdict(conclusion)
        payload["provenance"] = {
            node_id: [asdict(ev) for ev in evidence]
            for node_id, evidence in conclusion.provenance.items()
        }
        path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        return path

    @staticmethod
    def _fetch_evidence(retriever: Retriever, prompt: str, top_k: int) -> tuple[Evidence, ...]:
        raw = retriever(prompt, top_k)
        evidence: list[Evidence] = []
        for item in raw:
            query_result = QueryResult(**item)
            evidence.append(
                Evidence(
                    file_path=query_result.file_path,
                    start_line=query_result.start_line,
                    end_line=query_result.end_line,
                    score=query_result.score,
                    excerpt=query_result.text,
                    document_title=query_result.document_title,
                    section=query_result.section,
                    last_updated=query_result.last_updated,
                    access_scope=query_result.access_scope,
                    source_kind=query_result.source_kind,
                )
            )
        return tuple(evidence)

    @staticmethod
    def _default_answer(
        prompt: str,
        own_evidence: tuple[Evidence, ...],
        peer_evidence: tuple[Evidence, ...],
        round_index: int,
    ) -> str:
        if not own_evidence:
            return (
                f"Round {round_index} response for '{prompt}': insufficient evidence. "
                "Cannot provide grounded answer without retrieval hits."
            )

        own_lines = "; ".join(
            f"{ev.file_path}:{ev.start_line}-{ev.end_line}" for ev in own_evidence[:3]
        )
        peer_lines = "; ".join(
            f"{ev.file_path}:{ev.start_line}-{ev.end_line}" for ev in peer_evidence[:2]
        )
        return (
            f"Round {round_index} response for '{prompt}'. "
            f"Primary evidence: {own_lines or 'none'}. "
            f"Peer-cited evidence considered: {peer_lines or 'none'}."
        )

    @staticmethod
    def _estimate_tokens(text: str) -> int:
        return max(1, len(text.split()))

    def _build_prompt_context(self, prompt: str, evidence: tuple[Evidence, ...], token_budget: int) -> dict[str, object]:
        used = self._estimate_tokens(prompt) + 40
        selected: list[dict[str, str]] = []

        for item in evidence:
            citation = f"{item.file_path}:{item.start_line}-{item.end_line}"
            text = item.excerpt.replace("\n", " ").strip()
            chunk_tokens = self._estimate_tokens(text) + 12
            if used + chunk_tokens > token_budget:
                continue
            selected.append(
                {
                    "citation": citation,
                    "text": text,
                    "title": item.document_title,
                    "section": item.section,
                    "scope": item.access_scope,
                }
            )
            used += chunk_tokens

        return {"chunks": selected, "tokens_used": used, "token_budget": token_budget}

    def _log_grounding(self, prompt: str, grounded: bool, chunks_used: int, token_budget: int) -> None:
        event = {
            "timestamp": datetime.now(tz=UTC).isoformat(),
            "prompt": prompt,
            "grounded": grounded,
            "chunks_used": chunks_used,
            "token_budget": token_budget,
            "event": "retrieval_hit" if grounded else "retrieval_miss",
        }
        with self.retrieval_log_file.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(event) + "\n")
