"""Consensus package: contradiction detection, debate orchestration, and refinement loop."""

from .conflict_detector import ClaimComparison, ConflictDetector, ConflictReport
from .debate_engine import AgentResponse, DebateEngine, Evidence, MergedConclusion
from .refinement_loop import RefinementConfig, RefinementLoop, RefinementResult

__all__ = [
    "AgentResponse",
    "ClaimComparison",
    "ConflictDetector",
    "ConflictReport",
    "DebateEngine",
    "Evidence",
    "MergedConclusion",
    "RefinementConfig",
    "RefinementLoop",
    "RefinementResult",
]
