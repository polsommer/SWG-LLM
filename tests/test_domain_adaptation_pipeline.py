from __future__ import annotations

import unittest

from domain_adaptation.pipeline import (
    BenchmarkSuite,
    CanaryDeploymentManager,
    DataRefreshScheduler,
    DomainAdaptationProgram,
    EvalSample,
    FailureTaxonomy,
    HumanReviewRouter,
    InteractionQualityMetrics,
    LiveQualitySnapshot,
    NightlyRegressionEvaluator,
    OfflineComparator,
    ProductionInteraction,
    PromptResponseExample,
    QualityDashboardPublisher,
    ReviewedExampleRouter,
    ReviewedInteraction,
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

    def test_failure_taxonomy_and_lightweight_review_routing(self) -> None:
        taxonomy = FailureTaxonomy()
        self.assertEqual(taxonomy.classify(hallucination=True), "hallucination")
        self.assertEqual(taxonomy.classify(missed_context=True), "missed_context")
        self.assertEqual(taxonomy.classify(wrong_tool=True), "wrong_tool_choice")
        self.assertEqual(taxonomy.classify(incomplete_action=True), "incomplete_action")
        self.assertIsNone(taxonomy.classify())

        review_router = HumanReviewRouter(low_confidence_threshold=0.65)
        high_impact = ProductionInteraction(
            prompt="Reset payment account",
            response="Done",
            use_case="billing_ops",
            confidence=0.99,
            high_impact=True,
            metrics=InteractionQualityMetrics(0.9, 1.0, 500, 1.0, 0.8),
        )
        low_confidence = ProductionInteraction(
            prompt="Troubleshoot CI",
            response="Try step 1",
            use_case="dev_support",
            confidence=0.5,
            high_impact=False,
            metrics=InteractionQualityMetrics(0.6, 0.5, 900, 0.7, 0.4),
        )
        safe = ProductionInteraction(
            prompt="Where docs?",
            response="Answer: docs link",
            use_case="dev_support",
            confidence=0.9,
            high_impact=False,
            metrics=InteractionQualityMetrics(1.0, 1.0, 200, 1.0, 0.95),
        )

        self.assertTrue(review_router.needs_review(high_impact))
        self.assertTrue(review_router.needs_review(low_confidence))
        self.assertFalse(review_router.needs_review(safe))

    def test_reviewed_example_routing_regression_gate_and_dashboard(self) -> None:
        reviewed = [
            ReviewedInteraction(
                interaction=ProductionInteraction(
                    prompt="Approve refund",
                    response="Answer: escalate via billing runbook",
                    use_case="billing_ops",
                    confidence=0.6,
                    high_impact=True,
                    metrics=InteractionQualityMetrics(0.7, 0.5, 850, 0.6, 0.4),
                ),
                approved=False,
                failure_type="incomplete_action",
            ),
            ReviewedInteraction(
                interaction=ProductionInteraction(
                    prompt="Debug deploy",
                    response="Answer: inspect rollout logs",
                    use_case="dev_support",
                    confidence=0.8,
                    high_impact=False,
                    metrics=InteractionQualityMetrics(0.95, 1.0, 300, 1.0, 0.9),
                ),
                approved=True,
                failure_type=None,
            ),
        ]

        routed = ReviewedExampleRouter().route(reviewed)
        self.assertEqual(len(routed.training_examples), 1)
        self.assertEqual(len(routed.regression_eval_examples), 2)
        self.assertEqual(routed.training_examples[0].source, "review:dev_support")

        baseline = {
            "billing_ops": {"answer_correctness": 0.9, "task_success": 0.85},
            "dev_support": {"answer_correctness": 0.9, "task_success": 0.9},
        }
        current = {
            "billing_ops": {"answer_correctness": 0.8, "task_success": 0.79},
            "dev_support": {"answer_correctness": 0.89, "task_success": 0.88},
        }

        report = NightlyRegressionEvaluator(max_drop=0.03).evaluate(baseline=baseline, current=current)
        self.assertFalse(report.overall_passed)
        self.assertEqual(report.blocked_segments, ("billing_ops",))
        self.assertIn("answer_correctness", report.metric_drops["billing_ops"])

        dashboard = QualityDashboardPublisher().build_snapshot(reviewed)
        self.assertIn("answer_correctness", dashboard.overall_metrics)
        self.assertIn("billing_ops", dashboard.segmented_metrics)
        self.assertEqual(dashboard.failure_breakdown["incomplete_action"], 1)


if __name__ == "__main__":
    unittest.main()
