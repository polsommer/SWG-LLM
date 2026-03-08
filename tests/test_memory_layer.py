from __future__ import annotations

from pathlib import Path
import tempfile
import time
import unittest

from orchestrator.memory_layer import (
    DurableMemoryStore,
    MEMORY_SCOPE_GLOBAL,
    MEMORY_SCOPE_SESSION,
    MEMORY_SCOPE_TEAM,
    MEMORY_SCOPE_USER,
    MemoryItem,
    MemoryRequestContext,
    MemoryTelemetry,
    MemoryTelemetryEvent,
    ScopedMemoryRetriever,
)
from orchestrator.orchestration_layer import (
    CodeActionToolAdapter,
    DBReadToolAdapter,
    PlannerStage,
    TaskOrchestrator,
    TaskStateStore,
    VerifierStage,
)


class MemoryLayerTests(unittest.TestCase):
    def test_scope_isolation_and_retrieval_rules(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            store = DurableMemoryStore(Path(tmpdir) / "memory.json")
            retriever = ScopedMemoryRetriever(store)

            store.put(
                MemoryItem(
                    tenant_id="tenant-a",
                    scope=MEMORY_SCOPE_SESSION,
                    user_id="alice",
                    session_id="s-1",
                    category="preference",
                    content="Prefer concise markdown output",
                    tone="concise",
                    output_format="markdown",
                )
            )
            store.put(
                MemoryItem(
                    tenant_id="tenant-a",
                    scope=MEMORY_SCOPE_USER,
                    user_id="alice",
                    category="goal",
                    content="Recurring goal: track deployment regressions",
                    recurring_goal="track deployment regressions",
                )
            )
            store.put(
                MemoryItem(
                    tenant_id="tenant-a",
                    scope=MEMORY_SCOPE_TEAM,
                    team_id="platform",
                    category="fact",
                    content="Platform team deploys on Fridays",
                )
            )
            store.put(
                MemoryItem(
                    tenant_id="tenant-a",
                    scope=MEMORY_SCOPE_GLOBAL,
                    category="fact",
                    content="Use ISO-8601 timestamps in all logs",
                )
            )
            store.put(
                MemoryItem(
                    tenant_id="tenant-b",
                    scope=MEMORY_SCOPE_USER,
                    user_id="bob",
                    category="fact",
                    content="Secret from tenant-b",
                )
            )

            context = MemoryRequestContext(tenant_id="tenant-a", user_id="alice", team_id="platform", session_id="s-1")
            injected = retriever.retrieve("deployment markdown logs", context)
            self.assertEqual(len(injected.items), 4)
            self.assertEqual(injected.items[0].scope, MEMORY_SCOPE_SESSION)
            self.assertNotIn("tenant-b", {item.tenant_id for item in injected.items})

    def test_ttl_and_user_edit_delete_controls(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            store = DurableMemoryStore(Path(tmpdir) / "memory.json")
            context = MemoryRequestContext(tenant_id="tenant-a", user_id="alice", team_id="platform", session_id="s-1")

            item = store.put(
                MemoryItem(
                    tenant_id="tenant-a",
                    scope=MEMORY_SCOPE_USER,
                    user_id="alice",
                    category="preference",
                    content="long-form",
                    tone="detailed",
                ),
                ttl_seconds=1,
            )

            updated = store.edit(item.item_id, context, content="short-form", tone="concise")
            self.assertEqual(updated.content, "short-form")
            self.assertEqual(updated.tone, "concise")

            time.sleep(1.2)
            store.purge_expired()
            self.assertEqual(store.query(context), [])

            item2 = store.put(
                MemoryItem(
                    tenant_id="tenant-a",
                    scope=MEMORY_SCOPE_USER,
                    user_id="alice",
                    category="preference",
                    content="json",
                )
            )
            self.assertTrue(store.delete(item2.item_id, context))
            self.assertEqual(store.query(context), [])

    def test_continuity_across_turns_and_sessions_with_telemetry(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            memory_store = DurableMemoryStore(Path(tmpdir) / "memory.json")
            memory_store.put(
                MemoryItem(
                    tenant_id="tenant-a",
                    scope=MEMORY_SCOPE_USER,
                    user_id="alice",
                    category="goal",
                    content="Recurring goal: produce release-readiness checks",
                )
            )
            memory_store.put(
                MemoryItem(
                    tenant_id="tenant-a",
                    scope=MEMORY_SCOPE_SESSION,
                    user_id="alice",
                    session_id="session-1",
                    category="preference",
                    content="Tone should be concise",
                )
            )

            telemetry = MemoryTelemetry()
            orchestrator = TaskOrchestrator(
                planner=PlannerStage(),
                verifier=VerifierStage(),
                state_store=TaskStateStore(Path(tmpdir) / "state"),
                adapters={
                    "db_read": DBReadToolAdapter(db={"tasks": {"release check": {"ok": True}}}),
                    "code_action": CodeActionToolAdapter(),
                },
                memory_retriever=ScopedMemoryRetriever(memory_store),
                memory_telemetry=telemetry,
            )

            context_session_1 = MemoryRequestContext(tenant_id="tenant-a", user_id="alice", team_id="platform", session_id="session-1")
            turn1 = orchestrator.run("task-memory-1", "release check", max_attempts=1, memory_context=context_session_1)
            self.assertGreaterEqual(len(turn1.memory_injection), 2)

            context_session_2 = MemoryRequestContext(tenant_id="tenant-a", user_id="alice", team_id="platform", session_id="session-2")
            turn2 = orchestrator.run("task-memory-2", "release check", max_attempts=1, memory_context=context_session_2)
            self.assertTrue(all(item["scope"] != MEMORY_SCOPE_SESSION for item in turn2.memory_injection))
            self.assertTrue(any(item["scope"] == MEMORY_SCOPE_USER for item in turn2.memory_injection))

            orchestrator.record_memory_telemetry(
                request_id="req-1",
                context=context_session_1,
                usefulness_score=0.8,
                hallucination_risk_score=0.2,
                state=turn1,
            )
            summary = telemetry.summary()
            self.assertEqual(summary["events"], 1.0)
            self.assertGreater(summary["avg_usefulness"], summary["avg_hallucination_risk"])

            telemetry.record(
                MemoryTelemetryEvent(
                    request_id="req-2",
                    tenant_id="tenant-a",
                    memory_item_ids=(),
                    usefulness_score=0.5,
                    hallucination_risk_score=0.4,
                )
            )
            self.assertEqual(telemetry.summary()["events"], 2.0)


if __name__ == "__main__":
    unittest.main()
