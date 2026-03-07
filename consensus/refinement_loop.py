"""Iterative refinement loop for disagreement resolution between two agents."""

from __future__ import annotations

from dataclasses import dataclass

from .conflict_detector import ConflictDetector, ConflictReport
from .debate_engine import AgentResponse, DebateEngine, MergedConclusion


@dataclass(frozen=True)
class RefinementConfig:
    """Config for termination thresholds and iteration budget."""

    disagreement_threshold: float = 0.35
    agreement_threshold: float = 0.75
    max_rounds: int = 3


@dataclass(frozen=True)
class RefinementResult:
    """Output from iterative refinement and merge stage."""

    conclusion: MergedConclusion
    final_response_a: AgentResponse
    final_response_b: AgentResponse
    final_conflict_report: ConflictReport
    rounds_used: int
    converged: bool


class RefinementLoop:
    """Runs debate rounds until convergence criteria or max rounds are reached."""

    def __init__(
        self,
        debate_engine: DebateEngine,
        conflict_detector: ConflictDetector | None = None,
        config: RefinementConfig | None = None,
    ) -> None:
        self.debate_engine = debate_engine
        self.conflict_detector = conflict_detector or ConflictDetector()
        self.config = config or RefinementConfig()

    def run(self, prompt: str, top_k: int = 5) -> RefinementResult:
        response_a, response_b = self.debate_engine.initial_responses(prompt=prompt, top_k=top_k)
        report = self.conflict_detector.detect(response_a.answer, response_b.answer)

        rounds_used = 0
        for round_index in range(1, self.config.max_rounds + 1):
            if self._has_converged(report):
                break
            if report.disagreement_score <= self.config.disagreement_threshold:
                break

            response_a = self.debate_engine.revised_response(
                node_id=response_a.node_id,
                prompt=prompt,
                own_evidence=response_a.evidence,
                peer_evidence=response_b.evidence,
                round_index=round_index,
            )
            response_b = self.debate_engine.revised_response(
                node_id=response_b.node_id,
                prompt=prompt,
                own_evidence=response_b.evidence,
                peer_evidence=response_a.evidence,
                round_index=round_index,
            )
            report = self.conflict_detector.detect(response_a.answer, response_b.answer)
            rounds_used = round_index

        agreement = 1.0 - report.disagreement_score
        conclusion = self.debate_engine.merge_conclusions(
            prompt=prompt,
            response_a=response_a,
            response_b=response_b,
            agreement_score=agreement,
            rounds_used=rounds_used,
        )
        return RefinementResult(
            conclusion=conclusion,
            final_response_a=response_a,
            final_response_b=response_b,
            final_conflict_report=report,
            rounds_used=rounds_used,
            converged=self._has_converged(report),
        )

    def run_and_persist(self, prompt: str, top_k: int = 5, filename: str | None = None) -> RefinementResult:
        result = self.run(prompt=prompt, top_k=top_k)
        self.debate_engine.persist_conclusion(result.conclusion, filename=filename)
        return result

    def _has_converged(self, report: ConflictReport) -> bool:
        return (1.0 - report.disagreement_score) >= self.config.agreement_threshold
