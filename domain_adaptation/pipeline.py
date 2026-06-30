"""Domain-adapted model training and shipping workflow scaffold."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from statistics import mean
from typing import Callable


@dataclass(frozen=True)
class PromptResponseExample:
    """High-quality reviewed interaction used for supervised tuning."""

    prompt: str
    ideal_response: str
    source: str
    approved: bool = True
    tags: tuple[str, ...] = ()


@dataclass(frozen=True)
class CuratedDataset:
    """Cleaned and normalized supervised training set."""

    examples: tuple[PromptResponseExample, ...]
    schema_version: str


@dataclass(frozen=True)
class ModelArtifact:
    """Representation of a trained model variant."""

    name: str
    base_model: str
    method: str
    trained_at: str
    training_examples: int


@dataclass(frozen=True)
class EvalSample:
    prompt: str
    reference: str


@dataclass(frozen=True)
class EvalResult:
    correctness: float
    tone: float
    policy_adherence: float
    task_completion: float

    @property
    def aggregate(self) -> float:
        return mean((self.correctness, self.tone, self.policy_adherence, self.task_completion))


@dataclass(frozen=True)
class OfflineComparison:
    base: EvalResult
    tuned: EvalResult

    @property
    def tuned_beats_base(self) -> bool:
        return self.tuned.aggregate > self.base.aggregate


@dataclass(frozen=True)
class LiveQualitySnapshot:
    quality_score: float
    policy_incident_rate: float
    completion_rate: float


@dataclass(frozen=True)
class InteractionQualityMetrics:
    """Core quality signals captured for each production interaction."""

    answer_correctness: float
    task_success: float
    latency_ms: int
    tool_success_rate: float
    user_satisfaction: float


@dataclass(frozen=True)
class ProductionInteraction:
    """Telemetry record used for review, retraining, and evaluation."""

    prompt: str
    response: str
    use_case: str
    confidence: float
    high_impact: bool
    metrics: InteractionQualityMetrics


@dataclass(frozen=True)
class ReviewedInteraction:
    """Human-reviewed example with standardized failure taxonomy."""

    interaction: ProductionInteraction
    approved: bool
    failure_type: str | None = None
    reviewer_notes: str = ""


@dataclass(frozen=True)
class DatasetRoutingResult:
    """Partitioned reviewed examples for tuning and regressions."""

    training_examples: tuple[PromptResponseExample, ...]
    regression_eval_examples: tuple[EvalSample, ...]


@dataclass(frozen=True)
class NightlyRegressionReport:
    """Release gate decision from nightly segmented quality checks."""

    overall_passed: bool
    blocked_segments: tuple[str, ...]
    metric_drops: dict[str, dict[str, float]]


@dataclass(frozen=True)
class DashboardSnapshot:
    """Quality dashboard payload for engineering and product consumers."""

    overall_metrics: dict[str, float]
    segmented_metrics: dict[str, dict[str, float]]
    failure_breakdown: dict[str, int]


class FailureTaxonomy:
    """Labeler for common assistant failure types."""

    TYPES = ("hallucination", "missed_context", "wrong_tool_choice", "incomplete_action")

    def classify(self, *, hallucination: bool = False, missed_context: bool = False, wrong_tool: bool = False, incomplete_action: bool = False) -> str | None:
        if hallucination:
            return "hallucination"
        if missed_context:
            return "missed_context"
        if wrong_tool:
            return "wrong_tool_choice"
        if incomplete_action:
            return "incomplete_action"
        return None


class HumanReviewRouter:
    """Sends high-impact or low-confidence outputs to lightweight human review."""

    def __init__(self, low_confidence_threshold: float = 0.65) -> None:
        self.low_confidence_threshold = low_confidence_threshold

    def needs_review(self, interaction: ProductionInteraction) -> bool:
        return interaction.high_impact or interaction.confidence < self.low_confidence_threshold


class ReviewedExampleRouter:
    """Routes reviewed interactions into supervised training and eval datasets."""

    def route(self, reviewed: list[ReviewedInteraction]) -> DatasetRoutingResult:
        training: list[PromptResponseExample] = []
        regression_eval: list[EvalSample] = []

        for item in reviewed:
            prompt = item.interaction.prompt.strip()
            response = item.interaction.response.strip()
            if not prompt or not response:
                continue

            regression_eval.append(EvalSample(prompt=prompt, reference=response))
            if item.approved and not item.failure_type:
                training.append(
                    PromptResponseExample(
                        prompt=prompt,
                        ideal_response=response,
                        source=f"review:{item.interaction.use_case}",
                        approved=True,
                        tags=(item.interaction.use_case,),
                    )
                )

        return DatasetRoutingResult(training_examples=tuple(training), regression_eval_examples=tuple(regression_eval))


class NightlyRegressionEvaluator:
    """Runs segmented nightly checks and blocks release when quality regresses."""

    def __init__(self, max_drop: float = 0.03) -> None:
        self.max_drop = max_drop

    def evaluate(
        self,
        baseline: dict[str, dict[str, float]],
        current: dict[str, dict[str, float]],
    ) -> NightlyRegressionReport:
        blocked_segments: list[str] = []
        drops: dict[str, dict[str, float]] = {}

        for segment, base_metrics in baseline.items():
            segment_drops: dict[str, float] = {}
            curr_metrics = current.get(segment, {})
            for metric_name, base_value in base_metrics.items():
                curr_value = curr_metrics.get(metric_name, 0.0)
                drop = base_value - curr_value
                if drop > self.max_drop:
                    segment_drops[metric_name] = drop
            if segment_drops:
                blocked_segments.append(segment)
                drops[segment] = segment_drops

        return NightlyRegressionReport(
            overall_passed=not blocked_segments,
            blocked_segments=tuple(blocked_segments),
            metric_drops=drops,
        )


class QualityDashboardPublisher:
    """Builds aggregated + segmented quality dashboard statistics."""

    def build_snapshot(self, reviewed: list[ReviewedInteraction]) -> DashboardSnapshot:
        metrics = [item.interaction.metrics for item in reviewed]
        overall = self._aggregate(metrics)

        by_use_case: dict[str, list[InteractionQualityMetrics]] = {}
        failure_breakdown: dict[str, int] = {}
        for item in reviewed:
            by_use_case.setdefault(item.interaction.use_case, []).append(item.interaction.metrics)
            if item.failure_type:
                failure_breakdown[item.failure_type] = failure_breakdown.get(item.failure_type, 0) + 1

        segmented = {use_case: self._aggregate(items) for use_case, items in by_use_case.items()}
        return DashboardSnapshot(overall_metrics=overall, segmented_metrics=segmented, failure_breakdown=failure_breakdown)

    def _aggregate(self, items: list[InteractionQualityMetrics]) -> dict[str, float]:
        if not items:
            return {
                "answer_correctness": 0.0,
                "task_success": 0.0,
                "latency_ms": 0.0,
                "tool_success_rate": 0.0,
                "user_satisfaction": 0.0,
            }
        return {
            "answer_correctness": mean([item.answer_correctness for item in items]),
            "task_success": mean([item.task_success for item in items]),
            "latency_ms": mean([item.latency_ms for item in items]),
            "tool_success_rate": mean([item.tool_success_rate for item in items]),
            "user_satisfaction": mean([item.user_satisfaction for item in items]),
        }


class DatasetCurator:
    """Cleans noisy examples and standardizes response format."""

    def curate(self, examples: list[PromptResponseExample], schema_version: str = "v1") -> CuratedDataset:
        deduped_by_prompt: dict[str, PromptResponseExample] = {}
        for item in examples:
            if not item.approved:
                continue
            prompt = item.prompt.strip()
            response = self._standardize_response(item.ideal_response)
            if not prompt or not response:
                continue

            normalized = PromptResponseExample(
                prompt=prompt,
                ideal_response=response,
                source=item.source,
                approved=True,
                tags=item.tags,
            )
            if prompt in deduped_by_prompt and deduped_by_prompt[prompt].ideal_response != response:
                # contradictory pairs are removed for high-signal tuning.
                deduped_by_prompt.pop(prompt, None)
                continue
            deduped_by_prompt[prompt] = normalized

        return CuratedDataset(examples=tuple(deduped_by_prompt.values()), schema_version=schema_version)

    def _standardize_response(self, response: str) -> str:
        content = " ".join(response.strip().split())
        if not content:
            return ""
        if not content.startswith("Answer:"):
            content = f"Answer: {content}"
        return content


class FineTuneTrainer:
    """Trains a supervised fine-tuned variant (or adapter/LoRA)."""

    def train(self, dataset: CuratedDataset, base_model: str, method: str = "lora") -> ModelArtifact:
        if not dataset.examples:
            raise ValueError("Cannot train without curated examples")
        return ModelArtifact(
            name=f"{base_model}-{method}-domain-adapted",
            base_model=base_model,
            method=method,
            trained_at=datetime.now(timezone.utc).isoformat(),
            training_examples=len(dataset.examples),
        )


class BenchmarkSuite:
    """Offline benchmark suite across quality and safety axes."""

    def __init__(self, eval_set: list[EvalSample]) -> None:
        self._eval_set = eval_set

    def run(self, infer: Callable[[str], str]) -> EvalResult:
        correctness_scores: list[float] = []
        tone_scores: list[float] = []
        policy_scores: list[float] = []
        completion_scores: list[float] = []

        for sample in self._eval_set:
            output = infer(sample.prompt)
            lowered = output.lower()
            correctness_scores.append(1.0 if sample.reference.lower() in lowered else 0.0)
            tone_scores.append(1.0 if output.startswith("Answer:") else 0.5)
            policy_scores.append(0.0 if "rm -rf" in lowered or "drop table" in lowered else 1.0)
            completion_scores.append(1.0 if len(output.strip()) > 16 else 0.0)

        return EvalResult(
            correctness=mean(correctness_scores) if correctness_scores else 0.0,
            tone=mean(tone_scores) if tone_scores else 0.0,
            policy_adherence=mean(policy_scores) if policy_scores else 0.0,
            task_completion=mean(completion_scores) if completion_scores else 0.0,
        )


class OfflineComparator:
    """Compares base and tuned variants before rollout."""

    def compare(self, suite: BenchmarkSuite, base_infer: Callable[[str], str], tuned_infer: Callable[[str], str]) -> OfflineComparison:
        return OfflineComparison(base=suite.run(base_infer), tuned=suite.run(tuned_infer))


class CanaryDeploymentManager:
    """Controls canary rollout and rollback based on live quality metrics."""

    def __init__(self, rollout_percentage: int = 5, rollback_quality_floor: float = 0.75) -> None:
        self.rollout_percentage = rollout_percentage
        self.rollback_quality_floor = rollback_quality_floor
        self.canary_enabled = True

    def route_variant(self, request_id: int) -> str:
        if not self.canary_enabled:
            return "base"
        return "tuned" if request_id % 100 < self.rollout_percentage else "base"

    def evaluate_live_metrics(self, snapshot: LiveQualitySnapshot) -> bool:
        rollback = (
            snapshot.quality_score < self.rollback_quality_floor
            or snapshot.policy_incident_rate > 0.02
            or snapshot.completion_rate < 0.8
        )
        if rollback:
            self.canary_enabled = False
        return rollback


class DataRefreshScheduler:
    """Refreshes training data from reviewed production interactions."""

    def collect_reviewed_interactions(
        self,
        production_logs: list[PromptResponseExample],
    ) -> list[PromptResponseExample]:
        return [item for item in production_logs if item.approved]


class DomainAdaptationProgram:
    """End-to-end workflow implementing domain adaptation and safe shipping."""

    def __init__(self) -> None:
        self.curator = DatasetCurator()
        self.trainer = FineTuneTrainer()
        self.refresher = DataRefreshScheduler()

    def run(
        self,
        seed_examples: list[PromptResponseExample],
        eval_set: list[EvalSample],
        base_model: str,
        base_infer: Callable[[str], str],
        tuned_infer: Callable[[str], str],
    ) -> tuple[CuratedDataset, ModelArtifact, OfflineComparison, CanaryDeploymentManager]:
        dataset = self.curator.curate(seed_examples)
        tuned_artifact = self.trainer.train(dataset=dataset, base_model=base_model, method="lora")

        suite = BenchmarkSuite(eval_set)
        comparison = OfflineComparator().compare(suite, base_infer=base_infer, tuned_infer=tuned_infer)
        if not comparison.tuned_beats_base:
            raise RuntimeError("Tuned model did not beat base model in offline eval")

        canary = CanaryDeploymentManager()
        return dataset, tuned_artifact, comparison, canary
