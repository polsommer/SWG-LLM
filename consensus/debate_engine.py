"""Inter-agent debate orchestration and result persistence."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
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
    ) -> MergedConclusion:
        winner = response_a if len(response_a.answer) >= len(response_b.answer) else response_b
        conclusion = (
            f"Node {response_a.node_id}: {response_a.answer}\n\n"
            f"Node {response_b.node_id}: {response_b.answer}\n\n"
            f"Merged emphasis: {winner.answer}"
        )
        confidence = max(0.0, min(1.0, agreement_score * 0.75 + 0.25))

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
