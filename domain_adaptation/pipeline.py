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
