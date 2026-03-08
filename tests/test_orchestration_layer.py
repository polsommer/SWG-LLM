from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from orchestrator.orchestration_layer import (
    APICallOutput,
    APICallToolAdapter,
    CodeActionToolAdapter,
    DBReadToolAdapter,
    PlannerStage,
    SearchInput,
    SearchOutput,
    SearchToolAdapter,
    TaskOrchestrator,
    TaskStateStore,
    VerifierStage,
)


class OrchestrationLayerTests(unittest.TestCase):
    def test_typed_adapter_validation(self) -> None:
        adapter = SearchToolAdapter(index={"hello": ["a", "b"]})
        output = adapter.execute(SearchInput(query="hello", top_k=1))
        self.assertIsInstance(output, SearchOutput)
        self.assertEqual(output.results, ("a",))

        with self.assertRaises(TypeError):
            adapter.execute({"query": "hello"})  # type: ignore[arg-type]

    def test_orchestrator_completes_with_verification_and_confidence(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            store = TaskStateStore(Path(tmpdir))
            orchestrator = TaskOrchestrator(
                planner=PlannerStage(),
                verifier=VerifierStage(),
                state_store=store,
                adapters={
                    "search": SearchToolAdapter(index={"research api orchestration": ["doc1", "doc2"]}),
                    "db_read": DBReadToolAdapter(db={"tasks": {"research api orchestration": {"context": "internal"}}}),
                    "api_call": APICallToolAdapter(responder=lambda _: APICallOutput(status_code=200, body={"remote": "ok"})),
                    "code_action": CodeActionToolAdapter(),
                },
            )

            state = orchestrator.run("task-1", "research api orchestration", max_attempts=2, confidence_threshold=0.7)
            self.assertEqual(state.status, "completed")
            self.assertGreaterEqual(state.verification["confidence"], 0.7)
            self.assertIn("search", state.outputs)
            self.assertIn("db_read", state.outputs)
            self.assertIn("api_call", state.outputs)
            self.assertIn("code_action", state.outputs)
            self.assertGreater(len(state.traces), 3)

    def test_retry_and_persistence_resume(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            store = TaskStateStore(Path(tmpdir))
            orchestrator = TaskOrchestrator(
                planner=PlannerStage(),
                verifier=VerifierStage(),
                state_store=store,
                adapters={
                    "db_read": DBReadToolAdapter(db={"tasks": {"api needs retries": {"context": "ok"}}}),
                    "code_action": CodeActionToolAdapter(),
                },
            )

            state = orchestrator.run("task-2", "api needs retries", max_attempts=2, confidence_threshold=0.8)
            self.assertEqual(state.status, "failed")
            self.assertEqual(state.attempt, 2)

            resumed = orchestrator.resume("task-2")
            self.assertEqual(resumed.status, "failed")
            self.assertEqual(resumed.current_step, "done")


if __name__ == "__main__":
    unittest.main()
