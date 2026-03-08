from __future__ import annotations

import unittest

from domain_adaptation.pipeline import (
    BenchmarkSuite,
    CanaryDeploymentManager,
    DataRefreshScheduler,
    DomainAdaptationProgram,
    EvalSample,
    LiveQualitySnapshot,
    OfflineComparator,
    PromptResponseExample,
)


class DomainAdaptationPipelineTests(unittest.TestCase):
    def test_curation_removes_noise_conflicts_and_standardizes(self) -> None:
        program = DomainAdaptationProgram()
        curated = program.curator.curate(
            [
                PromptResponseExample(prompt="How to onboard?", ideal_response="Use checklist", source="ticket-1", approved=True),
                PromptResponseExample(prompt="How to onboard?", ideal_response="Different answer", source="ticket-2", approved=True),
                PromptResponseExample(prompt="   ", ideal_response="No prompt", source="bad", approved=True),
                PromptResponseExample(prompt="Reset VPN", ideal_response="", source="bad2", approved=True),
                PromptResponseExample(prompt="Escalation path", ideal_response="follow runbook", source="ticket-3", approved=True),
                PromptResponseExample(prompt="Unreviewed", ideal_response="ignore", source="ticket-4", approved=False),
            ]
        )

        self.assertEqual(len(curated.examples), 1)
        self.assertEqual(curated.examples[0].prompt, "Escalation path")
        self.assertTrue(curated.examples[0].ideal_response.startswith("Answer:"))

    def test_offline_compare_base_vs_tuned(self) -> None:
        suite = BenchmarkSuite(
            [
                EvalSample(prompt="p1", reference="alpha"),
                EvalSample(prompt="p2", reference="beta"),
            ]
        )

        base_infer = lambda _p: "Answer: generic response"
        tuned_infer = lambda p: "Answer: alpha with precise steps" if p == "p1" else "Answer: beta with precise steps"

        comparison = OfflineComparator().compare(suite, base_infer, tuned_infer)
        self.assertTrue(comparison.tuned_beats_base)
        self.assertGreater(comparison.tuned.correctness, comparison.base.correctness)

    def test_canary_rollout_and_rollback_trigger(self) -> None:
        canary = CanaryDeploymentManager(rollout_percentage=10, rollback_quality_floor=0.8)
        self.assertEqual(canary.route_variant(3), "tuned")
        self.assertEqual(canary.route_variant(15), "base")

        rolled_back = canary.evaluate_live_metrics(
            LiveQualitySnapshot(quality_score=0.7, policy_incident_rate=0.0, completion_rate=0.9)
        )
        self.assertTrue(rolled_back)
        self.assertEqual(canary.route_variant(1), "base")

    def test_end_to_end_program_and_data_refresh(self) -> None:
        program = DomainAdaptationProgram()
        eval_set = [EvalSample(prompt="Install agent", reference="use package")]
        seed = [
            PromptResponseExample(
                prompt="Install agent",
                ideal_response="use package",
                source="reviewed-prod",
                approved=True,
            )
        ]

        base_infer = lambda _p: "Answer: unknown"
        tuned_infer = lambda _p: "Answer: use package"

        dataset, artifact, comparison, _canary = program.run(
            seed_examples=seed,
            eval_set=eval_set,
            base_model="gpt-base",
            base_infer=base_infer,
            tuned_infer=tuned_infer,
        )

        self.assertEqual(artifact.method, "lora")
        self.assertEqual(dataset.examples[0].source, "reviewed-prod")
        self.assertTrue(comparison.tuned_beats_base)

        refreshed = DataRefreshScheduler().collect_reviewed_interactions(
            [
                PromptResponseExample(prompt="A", ideal_response="x", source="prod", approved=True),
                PromptResponseExample(prompt="B", ideal_response="y", source="prod", approved=False),
            ]
        )
        self.assertEqual(len(refreshed), 1)


if __name__ == "__main__":
    unittest.main()
