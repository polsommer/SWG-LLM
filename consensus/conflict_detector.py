"""Claim-level contradiction and semantic conflict scoring utilities."""

from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Iterable


_SENTENCE_SPLIT = re.compile(r"(?<=[.!?])\s+")
_TOKEN_SPLIT = re.compile(r"[^a-z0-9]+")
_NEGATION_MARKERS = {
    "no",
    "not",
    "never",
    "none",
    "cannot",
    "can't",
    "wont",
    "won't",
    "without",
    "false",
    "incorrect",
    "wrong",
}


@dataclass(frozen=True)
class Claim:
    """Atomic statement extracted from an answer."""

    text: str
    tokens: tuple[str, ...]


@dataclass(frozen=True)
class ClaimComparison:
    """Relationship between a claim pair."""

    left_claim: str
    right_claim: str
    topic_overlap: float
    semantic_similarity: float
    contradiction_score: float


@dataclass(frozen=True)
class ConflictReport:
    """Overall disagreement signal used by refinement loop."""

    disagreement_score: float
    semantic_alignment: float
    contradiction_pairs: tuple[ClaimComparison, ...]


class ConflictDetector:
    """Detect contradiction and semantic disagreement between two answers."""

    def detect(self, left_answer: str, right_answer: str) -> ConflictReport:
        left_claims = self.extract_claims(left_answer)
        right_claims = self.extract_claims(right_answer)

        if not left_claims or not right_claims:
            return ConflictReport(
                disagreement_score=1.0,
                semantic_alignment=0.0,
                contradiction_pairs=(),
            )

        comparisons: list[ClaimComparison] = []
        for left_claim in left_claims:
            best = max(
                (self._compare_claims(left_claim, right_claim) for right_claim in right_claims),
                key=lambda comp: comp.semantic_similarity,
            )
            comparisons.append(best)

        semantic_alignment = sum(comp.semantic_similarity for comp in comparisons) / len(comparisons)
        contradiction_weight = sum(comp.contradiction_score for comp in comparisons) / len(comparisons)
        disagreement = max(0.0, min(1.0, (1.0 - semantic_alignment) * 0.5 + contradiction_weight * 0.5))

        contradiction_pairs = tuple(
            comp for comp in comparisons if comp.contradiction_score >= 0.4 and comp.topic_overlap >= 0.2
        )

        return ConflictReport(
            disagreement_score=disagreement,
            semantic_alignment=semantic_alignment,
            contradiction_pairs=contradiction_pairs,
        )

    def extract_claims(self, answer: str) -> list[Claim]:
        """Split answer into sentence-like claims and normalize tokens."""
        claims: list[Claim] = []
        for fragment in _SENTENCE_SPLIT.split(answer.strip()):
            cleaned = fragment.strip().strip("-• ")
            if not cleaned:
                continue
            tokens = tuple(token for token in self._tokenize(cleaned) if token)
            if tokens:
                claims.append(Claim(text=cleaned, tokens=tokens))
        return claims

    @staticmethod
    def _tokenize(text: str) -> list[str]:
        return [token for token in _TOKEN_SPLIT.split(text.lower()) if token]

    def _compare_claims(self, left: Claim, right: Claim) -> ClaimComparison:
        left_terms = set(left.tokens)
        right_terms = set(right.tokens)

        overlap = self._jaccard(left_terms, right_terms)
        contradiction = self._contradiction_score(left_terms, right_terms)

        return ClaimComparison(
            left_claim=left.text,
            right_claim=right.text,
            topic_overlap=overlap,
            semantic_similarity=max(0.0, overlap - contradiction * 0.3),
            contradiction_score=contradiction,
        )

    @staticmethod
    def _jaccard(left_terms: set[str], right_terms: set[str]) -> float:
        union = left_terms | right_terms
        if not union:
            return 0.0
        return len(left_terms & right_terms) / len(union)

    def _contradiction_score(self, left_terms: set[str], right_terms: set[str]) -> float:
        topic_similarity = self._topic_similarity(left_terms, right_terms)
        if topic_similarity == 0:
            return 0.0

        left_has_negation = any(term in _NEGATION_MARKERS for term in left_terms)
        right_has_negation = any(term in _NEGATION_MARKERS for term in right_terms)
        if left_has_negation == right_has_negation:
            return 0.0

        return min(1.0, 0.4 + topic_similarity * 0.6)

    @staticmethod
    def _topic_similarity(left_terms: Iterable[str], right_terms: Iterable[str]) -> float:
        left_topic = {term for term in left_terms if term not in _NEGATION_MARKERS}
        right_topic = {term for term in right_terms if term not in _NEGATION_MARKERS}
        union = left_topic | right_topic
        if not union:
            return 0.0
        return len(left_topic & right_topic) / len(union)
