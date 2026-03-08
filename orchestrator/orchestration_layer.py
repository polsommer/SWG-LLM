"""Task orchestration layer with planning, tool execution, verification, and persistence."""

from __future__ import annotations

from dataclasses import asdict, dataclass, field, is_dataclass
from datetime import datetime, timezone
import json
from pathlib import Path
from typing import Any, Callable, Generic, TypeVar

from .memory_layer import MemoryRequestContext, MemoryTelemetry, MemoryTelemetryEvent, ScopedMemoryRetriever


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass(frozen=True)
class Subtask:
    """Explicit unit of work derived from user intent."""

    step_id: str
    objective: str
    tool: str
    required: bool = True


@dataclass(frozen=True)
class Plan:
    """Planner output containing explicit ordered steps."""

    task_id: str
    intent: str
    subtasks: tuple[Subtask, ...]


@dataclass(frozen=True)
class SearchInput:
    query: str
    top_k: int = 3


@dataclass(frozen=True)
class SearchOutput:
    results: tuple[str, ...]


@dataclass(frozen=True)
class DBReadInput:
    table: str
    key: str


@dataclass(frozen=True)
class DBReadOutput:
    record: dict[str, Any]


@dataclass(frozen=True)
class APICallInput:
    endpoint: str
    method: str = "GET"
    payload: dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class APICallOutput:
    status_code: int
    body: dict[str, Any]


@dataclass(frozen=True)
class CodeActionInput:
    action: str
    target: str
    content: str


@dataclass(frozen=True)
class CodeActionOutput:
    applied: bool
    summary: str


I = TypeVar("I")
O = TypeVar("O")


class ToolAdapter(Generic[I, O]):
    """Typed adapter with runtime schema checks."""

    input_type: type[Any]
    output_type: type[Any]

    def execute(self, payload: I) -> O:
        self._validate(payload, self.input_type, "input")
        response = self._run(payload)
        self._validate(response, self.output_type, "output")
        return response

    def _run(self, payload: I) -> O:
        raise NotImplementedError

    @staticmethod
    def _validate(value: Any, schema: type[Any], label: str) -> None:
        if is_dataclass(value):
            valid = isinstance(value, schema)
        else:
            valid = isinstance(value, schema)
        if not valid:
            raise TypeError(f"Invalid {label} payload type. Expected {schema.__name__}, got {type(value).__name__}")


class SearchToolAdapter(ToolAdapter[SearchInput, SearchOutput]):
    input_type = SearchInput
    output_type = SearchOutput

    def __init__(self, index: dict[str, list[str]] | None = None) -> None:
        self._index = index or {}

    def _run(self, payload: SearchInput) -> SearchOutput:
        hits = self._index.get(payload.query.lower(), [])[: payload.top_k]
        return SearchOutput(results=tuple(hits))


class DBReadToolAdapter(ToolAdapter[DBReadInput, DBReadOutput]):
    input_type = DBReadInput
    output_type = DBReadOutput

    def __init__(self, db: dict[str, dict[str, dict[str, Any]]] | None = None) -> None:
        self._db = db or {}

    def _run(self, payload: DBReadInput) -> DBReadOutput:
        table = self._db.get(payload.table, {})
        return DBReadOutput(record=dict(table.get(payload.key, {})))


class APICallToolAdapter(ToolAdapter[APICallInput, APICallOutput]):
    input_type = APICallInput
    output_type = APICallOutput

    def __init__(self, responder: Callable[[APICallInput], APICallOutput] | None = None) -> None:
        self._responder = responder or (lambda req: APICallOutput(status_code=200, body={"ok": True, "endpoint": req.endpoint}))

    def _run(self, payload: APICallInput) -> APICallOutput:
        return self._responder(payload)


class CodeActionToolAdapter(ToolAdapter[CodeActionInput, CodeActionOutput]):
    input_type = CodeActionInput
    output_type = CodeActionOutput

    def _run(self, payload: CodeActionInput) -> CodeActionOutput:
        summary = f"{payload.action} on {payload.target}"
        return CodeActionOutput(applied=True, summary=summary)


@dataclass(frozen=True)
class VerificationReport:
    complete: bool
    policy_compliant: bool
    factually_consistent: bool
    confidence: float
    issues: tuple[str, ...] = ()

    @property
    def passed(self) -> bool:
        return self.complete and self.policy_compliant and self.factually_consistent


class VerifierStage:
    """Checks completion, policy, and factual consistency before completion."""

    banned_terms = ("drop table", "rm -rf")

    def verify(self, plan: Plan, outputs: dict[str, Any]) -> VerificationReport:
        issues: list[str] = []

        missing = [step.step_id for step in plan.subtasks if step.required and step.step_id not in outputs]
        complete = not missing
        if missing:
            issues.append(f"missing outputs for: {', '.join(missing)}")

        serialized = json.dumps({k: self._normalize(v) for k, v in outputs.items()}).lower()
        policy_compliant = not any(term in serialized for term in self.banned_terms)
        if not policy_compliant:
            issues.append("policy violation detected in tool output")

        factual_consistent = all(self._has_signal(outputs.get(step.step_id)) for step in plan.subtasks if step.required)
        if not factual_consistent:
            issues.append("factual consistency check failed for at least one required step")

        confidence = self._confidence(complete, policy_compliant, factual_consistent, len(issues))
        return VerificationReport(
            complete=complete,
            policy_compliant=policy_compliant,
            factually_consistent=factual_consistent,
            confidence=confidence,
            issues=tuple(issues),
        )

    def _has_signal(self, payload: Any) -> bool:
        if payload is None:
            return False
        normalized = self._normalize(payload)
        if isinstance(normalized, dict):
            return bool(normalized)
        if isinstance(normalized, (list, tuple, str)):
            return len(normalized) > 0
        return True

    def _normalize(self, payload: Any) -> Any:
        if is_dataclass(payload):
            return asdict(payload)
        return payload

    @staticmethod
    def _confidence(complete: bool, policy: bool, factual: bool, issue_count: int) -> float:
        score = 0.0
        score += 0.4 if complete else 0.0
        score += 0.3 if policy else 0.0
        score += 0.3 if factual else 0.0
        score -= issue_count * 0.05
        return max(0.0, min(1.0, score))


@dataclass
class TraceEvent:
    timestamp: str
    stage: str
    message: str
    data: dict[str, Any] = field(default_factory=dict)


@dataclass
class TaskState:
    task_id: str
    intent: str
    status: str
    attempt: int
    max_attempts: int
    confidence_threshold: float
    current_step: str = "planning"
    outputs: dict[str, Any] = field(default_factory=dict)
    memory_injection: list[dict[str, Any]] = field(default_factory=list)
    verification: dict[str, Any] = field(default_factory=dict)
    traces: list[TraceEvent] = field(default_factory=list)


class TaskStateStore:
    """JSON persistence for resumable orchestration state."""

    def __init__(self, directory: Path) -> None:
        self._directory = directory
        self._directory.mkdir(parents=True, exist_ok=True)

    def save(self, state: TaskState) -> Path:
        path = self._directory / f"{state.task_id}.json"
        with path.open("w", encoding="utf-8") as handle:
            json.dump(self._serialize_state(state), handle, indent=2, sort_keys=True)
        return path

    def load(self, task_id: str) -> TaskState:
        path = self._directory / f"{task_id}.json"
        with path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
        return self._deserialize_state(payload)

    def _serialize_state(self, state: TaskState) -> dict[str, Any]:
        payload = asdict(state)
        payload["outputs"] = {key: self._serialize_value(value) for key, value in state.outputs.items()}
        return payload

    def _serialize_value(self, value: Any) -> Any:
        if is_dataclass(value):
            return {"__dataclass__": value.__class__.__name__, "value": asdict(value)}
        return value

    def _deserialize_state(self, payload: dict[str, Any]) -> TaskState:
        traces = [TraceEvent(**item) for item in payload.get("traces", [])]
        outputs = {key: self._deserialize_value(value) for key, value in payload.get("outputs", {}).items()}
        return TaskState(
            task_id=payload["task_id"],
            intent=payload["intent"],
            status=payload["status"],
            attempt=payload["attempt"],
            max_attempts=payload["max_attempts"],
            confidence_threshold=payload["confidence_threshold"],
            current_step=payload.get("current_step", "planning"),
            outputs=outputs,
            memory_injection=payload.get("memory_injection", []),
            verification=payload.get("verification", {}),
            traces=traces,
        )

    def _deserialize_value(self, value: Any) -> Any:
        if not isinstance(value, dict) or "__dataclass__" not in value:
            return value

        mapping: dict[str, type[Any]] = {
            "SearchOutput": SearchOutput,
            "DBReadOutput": DBReadOutput,
            "APICallOutput": APICallOutput,
            "CodeActionOutput": CodeActionOutput,
        }
        cls = mapping.get(value["__dataclass__"])
        if cls is None:
            return value.get("value")
        return cls(**value["value"])


class PlannerStage:
    """Converts user intent into explicit subtasks."""

    def build_plan(self, task_id: str, intent: str) -> Plan:
        lower = intent.lower()
        steps: list[Subtask] = []
        if any(token in lower for token in ("research", "search", "find")):
            steps.append(Subtask("search", "Gather external context", "search"))
        steps.append(Subtask("db_read", "Load internal context", "db_read"))
        if any(token in lower for token in ("api", "sync", "fetch")):
            steps.append(Subtask("api_call", "Fetch remote system state", "api_call"))
        steps.append(Subtask("code_action", "Apply code or task action", "code_action"))
        return Plan(task_id=task_id, intent=intent, subtasks=tuple(steps))


class TaskOrchestrator:
    """End-to-end orchestration engine with retry, stop conditions, persistence, and traces."""

    def __init__(
        self,
        planner: PlannerStage,
        verifier: VerifierStage,
        state_store: TaskStateStore,
        adapters: dict[str, ToolAdapter[Any, Any]],
        memory_retriever: ScopedMemoryRetriever | None = None,
        memory_telemetry: MemoryTelemetry | None = None,
    ) -> None:
        self._planner = planner
        self._verifier = verifier
        self._state_store = state_store
        self._adapters = adapters
        self._memory_retriever = memory_retriever
        self._memory_telemetry = memory_telemetry

    def run(
        self,
        task_id: str,
        intent: str,
        *,
        max_attempts: int = 3,
        confidence_threshold: float = 0.8,
        memory_context: MemoryRequestContext | None = None,
    ) -> TaskState:
        state = TaskState(
            task_id=task_id,
            intent=intent,
            status="running",
            attempt=0,
            max_attempts=max_attempts,
            confidence_threshold=confidence_threshold,
        )
        self._trace(state, "planning", "building plan from intent")
        if self._memory_retriever is not None and memory_context is not None:
            memory_injection = self._memory_retriever.retrieve(query=intent, context=memory_context)
            state.memory_injection = [asdict(item) for item in memory_injection.items]
            self._trace(
                state,
                "memory",
                "memory injected",
                {"count": len(memory_injection.items), "scopes": [item.scope for item in memory_injection.items]},
            )
        plan = self._planner.build_plan(task_id=task_id, intent=intent)
        self._trace(state, "planning", "plan built", {"subtasks": [asdict(item) for item in plan.subtasks]})
        self._state_store.save(state)
        return self._execute(plan, state)

    def resume(self, task_id: str) -> TaskState:
        state = self._state_store.load(task_id)
        if state.status in {"completed", "failed", "stopped"}:
            self._trace(state, "resume", "state already terminal; no-op")
            self._state_store.save(state)
            return state
        plan = self._planner.build_plan(task_id=state.task_id, intent=state.intent)
        self._trace(state, "resume", "resuming execution")
        return self._execute(plan, state)

    def _execute(self, plan: Plan, state: TaskState) -> TaskState:
        while state.attempt < state.max_attempts:
            state.attempt += 1
            state.current_step = "execution"
            self._trace(state, "execution", "attempt started", {"attempt": state.attempt})

            state.outputs = {}
            for subtask in plan.subtasks:
                if subtask.tool not in self._adapters:
                    self._trace(state, "execution", "missing adapter", {"tool": subtask.tool})
                    continue
                output = self._run_tool(subtask.tool, state.intent)
                state.outputs[subtask.step_id] = output
                self._trace(state, "execution", "tool completed", {"step_id": subtask.step_id, "tool": subtask.tool})

            state.current_step = "verification"
            report = self._verifier.verify(plan, state.outputs)
            state.verification = asdict(report)
            self._trace(state, "verification", "verification completed", state.verification)

            if report.passed and report.confidence >= state.confidence_threshold:
                state.status = "completed"
                state.current_step = "done"
                self._trace(state, "stop", "completion criteria reached")
                self._state_store.save(state)
                return state

            self._trace(
                state,
                "retry",
                "verification failed; retrying" if state.attempt < state.max_attempts else "retry budget exhausted",
                {"issues": list(report.issues), "confidence": report.confidence},
            )
            self._state_store.save(state)

        state.status = "failed"
        state.current_step = "done"
        self._trace(state, "stop", "failed due to max attempts reached")
        self._state_store.save(state)
        return state

    def _run_tool(self, tool: str, intent: str) -> Any:
        adapter = self._adapters[tool]
        if tool == "search":
            return adapter.execute(SearchInput(query=intent.lower(), top_k=3))
        if tool == "db_read":
            return adapter.execute(DBReadInput(table="tasks", key=intent.lower()))
        if tool == "api_call":
            return adapter.execute(APICallInput(endpoint="/workflow/context", method="POST", payload={"intent": intent}))
        if tool == "code_action":
            return adapter.execute(CodeActionInput(action="compose", target="workflow", content=intent))
        raise ValueError(f"Unsupported tool: {tool}")

    def record_memory_telemetry(
        self,
        *,
        request_id: str,
        context: MemoryRequestContext,
        usefulness_score: float,
        hallucination_risk_score: float,
        state: TaskState,
    ) -> None:
        if self._memory_telemetry is None:
            return
        item_ids = tuple(item.get("item_id", "") for item in state.memory_injection)
        self._memory_telemetry.record(
            MemoryTelemetryEvent(
                request_id=request_id,
                tenant_id=context.tenant_id,
                memory_item_ids=item_ids,
                usefulness_score=usefulness_score,
                hallucination_risk_score=hallucination_risk_score,
            )
        )

    def _trace(self, state: TaskState, stage: str, message: str, data: dict[str, Any] | None = None) -> None:
        state.traces.append(TraceEvent(timestamp=_utc_now(), stage=stage, message=message, data=data or {}))


__all__ = [
    "APICallInput",
    "APICallOutput",
    "APICallToolAdapter",
    "CodeActionInput",
    "CodeActionOutput",
    "CodeActionToolAdapter",
    "DBReadInput",
    "DBReadOutput",
    "DBReadToolAdapter",
    "Plan",
    "PlannerStage",
    "SearchInput",
    "SearchOutput",
    "SearchToolAdapter",
    "Subtask",
    "TaskOrchestrator",
    "TaskState",
    "TaskStateStore",
    "ToolAdapter",
    "TraceEvent",
    "VerificationReport",
    "VerifierStage",
]
