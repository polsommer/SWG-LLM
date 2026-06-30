from __future__ import annotations

import json
import re
from datetime import datetime, UTC
from typing import Any

import requests

from .approval import ApprovalManager
from .indexer import ProjectIndexer
from .prompts import SYSTEM_PROMPT
from .sandbox import PythonSandbox
from .session_manager import SessionManager
from .storage import (
    LESSONS_FILE,
    OBSERVABILITY_FILE,
    RUN_LOG_FILE,
    append_json_row,
    collect_workspace_context,
    load_recent_lessons,
    read_snippet,
    resolve_workspace_file,
    save_generated_file,
    summarize_paths,
)


FILE_BLOCK_RE = re.compile(
    r"<<<FILE:(?P<path>.*?)>>>\s*(?P<content>.*?)\s*<<<END FILE>>>",
    re.DOTALL,
)
JSON_FENCE_RE = re.compile(r"```(?:json)?\s*(?P<body>\{.*?\})\s*```", re.DOTALL | re.IGNORECASE)


class LocalAgent:
    def __init__(self, ollama_url: str = "http://127.0.0.1:11434/api/generate") -> None:
        self.ollama_url = ollama_url
        self.sandbox = PythonSandbox()
        self.indexer = ProjectIndexer()
        self.approval = ApprovalManager()
        self.sessions = SessionManager()

    def _log_observation(self, session_id: str, kind: str, details: dict[str, Any]) -> None:
        append_json_row(
            OBSERVABILITY_FILE,
            {
                "timestamp": datetime.now(UTC).isoformat(),
                "session_id": session_id,
                "kind": kind,
                "details": details,
            },
        )

    def _tool_spec(self) -> str:
        return (
            "Supported tools:\n"
            "- list_files: arguments {}\n"
            "- read_file: arguments {\"path\": \"uploads/name.txt\"} or {\"path\": \"generated/name.txt\"}\n"
            "- write_file: arguments {\"path\": \"notes/output.md\", \"content\": \"...\"} writes only under generated/\n"
            "- run_python: arguments {\"code\": \"print('hi')\", \"input_paths\": [\"uploads/data.csv\"]} uses a guarded local runner, not a hardened sandbox\n"
            "- run_python_script: arguments {\"path\": \"generated/task.py\", \"input_paths\": [\"uploads/data.csv\"]} uses a guarded local runner, not a hardened sandbox\n"
            "- index_project: arguments {}\n"
            "- search_project: arguments {\"query\": \"CreatureObject login\", \"limit\": 6}\n"
            "- read_project_file: arguments {\"path\": \"swg-main/src/server/game/CreatureObject.cpp\"}\n"
            "- inspect_graph: arguments {\"query\": \"CreatureObject\", \"limit\": 6}\n"
        )

    def _build_prompt(self, user_message: str) -> tuple[str, list[str]]:
        context_items = collect_workspace_context()
        lessons = load_recent_lessons()
        project_status = self.indexer.get_status()
        repo_plan = self._build_repo_plan(user_message, project_status)

        context_lines = []
        for item in context_items:
            context_lines.append(f"FILE: {item['path']}\n{item['snippet']}")

        lessons_block = "\n".join(f"- {lesson}" for lesson in lessons) or "- No saved lessons yet."
        workspace_block = "\n\n".join(context_lines) or "No uploaded or generated files yet."

        prompt = (
            f"{SYSTEM_PROMPT.strip()}\n\n"
            f"{self._tool_spec()}\n\n"
            f"Project index status:\n"
            f"- indexed_at: {project_status.get('indexed_at')}\n"
            f"- file_count: {project_status.get('file_count')}\n"
            f"- chunk_count: {project_status.get('chunk_count')}\n"
            f"- roots: {', '.join(project_status.get('roots', []))}\n\n"
            f"{repo_plan}\n\n"
            f"Saved lessons:\n{lessons_block}\n\n"
            f"Workspace context:\n{workspace_block}\n\n"
            f"User request:\n{user_message.strip()}\n"
        )
        return prompt, lessons

    def _is_repo_question(self, user_message: str, project_status: dict[str, Any]) -> bool:
        if project_status.get("file_count", 0) <= 0:
            return False

        lowered = user_message.lower()
        repo_terms = [
            "repo",
            "repository",
            "codebase",
            "source",
            "src",
            "dsrc",
            "class",
            "function",
            "method",
            "file",
            "where is",
            "where does",
            "how does",
            "implement",
            "handler",
            "object",
            "cpp",
            ".h",
            ".py",
            ".java",
            "call",
            "inherit",
            "login",
        ]
        return any(term in lowered for term in repo_terms)

    def _suggest_repo_query(self, user_message: str) -> str:
        normalized = re.sub(r"[^A-Za-z0-9_./ -]+", " ", user_message).strip()
        tokens = [token for token in normalized.split() if len(token) >= 3]
        preferred: list[str] = []
        stop_words = {
            "what",
            "where",
            "when",
            "does",
            "this",
            "that",
            "with",
            "from",
            "into",
            "about",
            "would",
            "could",
            "should",
            "please",
            "show",
            "find",
            "tell",
            "repo",
            "codebase",
            "source",
        }
        for token in tokens:
            lowered = token.lower()
            if lowered in stop_words:
                continue
            preferred.append(token)
            if len(preferred) >= 4:
                break
        return " ".join(preferred) or normalized[:80] or "project flow"

    def _build_repo_plan(self, user_message: str, project_status: dict[str, Any]) -> str:
        if not self._is_repo_question(user_message, project_status):
            return "Repo-aware planning mode: inactive."

        suggested_query = self._suggest_repo_query(user_message)
        return (
            "Repo-aware planning mode: active.\n"
            "Suggested strategy:\n"
            "1. Search the project index before answering.\n"
            "2. Inspect the most relevant 1-3 files if search finds likely matches.\n"
            "3. Base the answer on inspected evidence, not just symbol names.\n"
            f"Suggested initial search query: {suggested_query}"
        )

    def _build_progress_notes(self, created_files: list[str], tool_events: list[str]) -> tuple[list[str], list[str], list[str]]:
        updates: list[str] = []
        figured_out: list[str] = []
        ideas: list[str] = []
        status = self.indexer.get_status()
        summary = status.get("summary", {})

        if status.get("indexed_at"):
            updates.append(
                f"Project index loaded from {status.get('indexed_at')} with {status.get('file_count', 0)} files and {status.get('chunk_count', 0)} chunks."
            )
        else:
            updates.append("Project index has not been built yet.")

        if created_files:
            updates.append(f"Created or updated {len(created_files)} generated file(s): {', '.join(created_files[:4])}.")

        if any("search_project" in event for event in tool_events):
            updates.append("Searched the indexed project with hybrid lexical and semantic retrieval to narrow the answer.")
        if any("read_project_file" in event for event in tool_events):
            updates.append("Opened specific project files to confirm details before answering.")
        if any("inspect_graph" in event for event in tool_events):
            updates.append("Traversed the cross-file code graph to connect definitions, calls, and references.")
        if any("run_python" in event for event in tool_events):
            updates.append("Ran a guarded local Python task to inspect or transform data.")
        if any('"status": "denied"' in event or '"error_type": "policy_denied"' in event for event in tool_events):
            updates.append("Guarded Python execution was denied by policy; this was blocked intentionally, not a normal script crash.")
        if any('"status": "failed"' in event or '"error_type": "runtime_error"' in event for event in tool_events):
            updates.append("A Python task started but failed at runtime.")
        if any('"status": "timed_out"' in event or '"error_type": "timeout"' in event for event in tool_events):
            updates.append("A Python task timed out before completion.")
        if self._tool_sequence_looks_repo_aware(tool_events):
            updates.append("Used a repo-aware answer flow: searched first, then inspected files before answering.")

        top_ext = summary.get("top_extensions", [])
        if top_ext:
            figured_out.append(
                "Most common indexed file types: " + ", ".join(f"{row['name']} ({row['count']})" for row in top_ext[:3]) + "."
            )

        top_symbols = summary.get("top_symbols", [])
        if top_symbols:
            figured_out.append(
                "Frequently seen symbols include " + ", ".join(row["name"] for row in top_symbols[:5]) + "."
            )

        top_imports = summary.get("top_imports", [])
        if top_imports:
            figured_out.append(
                "Common imports/includes include " + ", ".join(row["name"] for row in top_imports[:4]) + "."
            )

        top_inheritance = summary.get("top_inheritance", [])
        if top_inheritance:
            figured_out.append(
                "Repeated base types or inherited symbols include " + ", ".join(row["name"] for row in top_inheritance[:4]) + "."
            )

        top_connected = summary.get("top_connected_symbols", [])
        if top_connected:
            figured_out.append(
                "Cross-file graph highlights connected symbols like " + ", ".join(row["name"] for row in top_connected[:5]) + "."
            )

        semantic_terms = summary.get("semantic_top_terms", [])
        if semantic_terms:
            figured_out.append(
                "Semantic retrieval is anchored by frequent concepts like " + ", ".join(row["name"] for row in semantic_terms[:5]) + "."
            )

        largest_files = summary.get("largest_files", [])
        if largest_files:
            figured_out.append(
                "Large files worth checking first: " + ", ".join(str(row["path"]) for row in largest_files[:3]) + "."
            )

        if status.get("file_count", 0) == 0:
            ideas.append("Populate `swg-main/src` or `swg-main/dsrc`, then rebuild the project index so repo-aware answers have real code to inspect.")
        else:
            ideas.append("Ask focused repo questions like `where is login handled?` or `what owns creature state?` so the agent can search the index first.")
            ideas.append("Add semantic embeddings next if you want stronger retrieval than plain keyword scoring.")

        if not created_files:
            ideas.append("Use `Generate File` when you want the agent to turn findings into notes, plans, or draft patches.")

        return updates[:6], figured_out[:6], ideas[:6]

    def _tool_sequence_looks_repo_aware(self, tool_events: list[str]) -> bool:
        saw_search = False
        saw_read_after = False
        for event in tool_events:
            parsed = self._parse_tool_event(event)
            if not parsed:
                continue
            _, details = parsed
            tool_name = str(details["tool_name"])
            if tool_name == "search_project":
                saw_search = True
            elif saw_search and tool_name == "read_project_file":
                saw_read_after = True
        return saw_search and saw_read_after

    def _parse_tool_event(self, event: str) -> tuple[str, dict[str, Any]] | None:
        if ": " not in event:
            return None
        prefix, payload = event.split(": ", 1)
        parts = payload.strip().split(" ", 1)
        if not parts:
            return None
        tool_name = parts[0].strip()
        arguments: dict[str, Any] = {}
        if len(parts) > 1:
            try:
                parsed = json.loads(parts[1])
                if isinstance(parsed, dict):
                    arguments = parsed
            except json.JSONDecodeError:
                arguments = {}
        return prefix.strip(), {"tool_name": tool_name, "arguments": arguments}

    def _build_trust_report(
        self,
        reply: str,
        created_files: list[str],
        tool_events: list[str],
        updates: list[str],
        figured_out: list[str],
        ideas: list[str],
        requires_approval: bool,
    ) -> dict[str, Any]:
        searched_queries: list[str] = []
        inspected_paths: list[str] = []
        timeline: list[dict[str, str]] = []
        repo_strategy = "basic"

        for event in tool_events:
            parsed = self._parse_tool_event(event)
            if not parsed:
                timeline.append({"kind": "event", "label": event})
                continue

            prefix, details = parsed
            tool_name = str(details["tool_name"])
            arguments = details["arguments"]
            kind = "action" if "call" in prefix.lower() else "result"
            timeline.append({"kind": kind, "label": tool_name, "detail": json.dumps(arguments, ensure_ascii=True)})

            if tool_name == "search_project":
                query = str(arguments.get("query", "")).strip()
                if query and query not in searched_queries:
                    searched_queries.append(query)
            if tool_name == "inspect_graph":
                query = str(arguments.get("query", "")).strip()
                if query and f"graph:{query}" not in searched_queries:
                    searched_queries.append(f"graph:{query}")
            if tool_name in {"read_project_file", "read_file", "run_python_script"}:
                path = str(arguments.get("path", "")).strip()
                if path and path not in inspected_paths:
                    inspected_paths.append(path)

        if self._tool_sequence_looks_repo_aware(tool_events):
            repo_strategy = "search-then-inspect"
        elif searched_queries:
            repo_strategy = "search-only"

        evidence_count = 0
        if searched_queries:
            evidence_count += 1
        if inspected_paths:
            evidence_count += 2
        if created_files:
            evidence_count += 1
        if figured_out:
            evidence_count += 1
        if requires_approval:
            evidence_count = max(1, evidence_count - 1)

        confidence_score = min(100, 25 + evidence_count * 15)
        confidence_label = "Low"
        if confidence_score >= 75:
            confidence_label = "High"
        elif confidence_score >= 50:
            confidence_label = "Medium"

        basis = "Answer is based mostly on direct file inspection." if inspected_paths else "Answer is based mostly on indexed context and general reasoning."
        if searched_queries and not inspected_paths:
            basis = "Answer is based on indexed project search, but not much direct file inspection yet."
        if requires_approval:
            basis = "Agent paused before a risky step, so conclusions may still be incomplete."

        return {
            "confidence_score": confidence_score,
            "confidence_label": confidence_label,
            "basis": basis,
            "planning_mode": "repo-aware" if searched_queries or inspected_paths else "general",
            "repo_strategy": repo_strategy,
            "strategy_steps": [
                "Search indexed project",
                "Inspect likely files",
                "Compare evidence",
                "Answer with confidence notes",
            ] if searched_queries or inspected_paths else [
                "Use uploaded/generated context",
                "Answer directly or use tools if needed",
            ],
            "searched_queries": searched_queries[:6],
            "inspected_paths": inspected_paths[:8],
            "conclusions": figured_out[:6],
            "next_actions": ideas[:6],
            "timeline": timeline[:12],
            "reply_preview": reply[:240],
            "approval_waiting": requires_approval,
            "created_files": created_files[:8],
            "updates": updates[:6],
        }

    def _ollama_generate(self, model: str, prompt: str) -> str:
        response = requests.post(
            self.ollama_url,
            json={
                "model": model,
                "prompt": prompt,
                "stream": False,
            },
            timeout=180,
        )
        response.raise_for_status()
        payload: dict[str, Any] = response.json()
        return str(payload.get("response", "")).strip()

    def _try_parse_tool_json(self, candidate: str) -> dict[str, Any] | None:
        try:
            parsed = json.loads(candidate)
        except json.JSONDecodeError:
            return None
        if not isinstance(parsed, dict):
            return None
        tool_name = parsed.get("tool_name")
        arguments = parsed.get("arguments", {})
        if not isinstance(tool_name, str) or not isinstance(arguments, dict):
            return None
        return {"tool_name": tool_name, "arguments": arguments}

    def _parse_tool_call(self, reply: str, session_id: str) -> dict[str, Any] | None:
        stripped = reply.strip()
        parsed = self._try_parse_tool_json(stripped)
        if parsed:
            return parsed

        fence_match = JSON_FENCE_RE.search(stripped)
        if fence_match:
            parsed = self._try_parse_tool_json(fence_match.group("body").strip())
            if parsed:
                return parsed

        decoder = json.JSONDecoder()
        for index, char in enumerate(stripped):
            if char != "{":
                continue
            try:
                candidate, end = decoder.raw_decode(stripped[index:])
            except json.JSONDecodeError:
                continue
            if end <= 0:
                continue
            parsed = self._try_parse_tool_json(json.dumps(candidate, ensure_ascii=True))
            if parsed:
                return parsed
        if "\"tool_name\"" in stripped or stripped.startswith("{") or stripped.startswith("```"):
            reason = "Model response looked tool-shaped but was not parseable as a supported tool call."
            self.sessions.record_parse_failure(session_id, reason)
            self._log_observation(
                session_id,
                "tool_parse_failed",
                {
                    "reason": reason,
                    "reply_preview": stripped[:400],
                },
            )
        return None

    def _build_followup_prompt(self, base_prompt: str, transcript: list[dict[str, str]]) -> str:
        parts = [base_prompt.rstrip(), "", "Tool interaction transcript:"]
        if not transcript:
            parts.append("- No tool activity yet.")
        else:
            for index, item in enumerate(transcript, start=1):
                parts.append(f"Step {index} tool call:")
                parts.append(item["tool_call"])
                parts.append("Step result:")
                parts.append(item["tool_result"])
        parts.extend(
            [
                "",
                "Continue from the transcript above.",
                "If more tool use is needed, return one JSON tool call only.",
                "Otherwise answer the user normally.",
            ]
        )
        return "\n".join(parts)

    def _execute_tool(self, tool_name: str, arguments: dict[str, Any], session_id: str) -> tuple[str, list[str]]:
        if tool_name == "list_files":
            context_items = collect_workspace_context(limit=100)
            paths = [item["path"] for item in context_items]
            return json.dumps({"files": paths}, ensure_ascii=True), []

        if tool_name == "read_file":
            path = str(arguments.get("path", "")).strip()
            if not path:
                raise ValueError("read_file requires a path")
            target = resolve_workspace_file(path)
            if not target.exists() or not target.is_file():
                raise ValueError(f"File not found: {path}")
            return json.dumps({"path": path, "content": read_snippet(target, max_chars=12000)}, ensure_ascii=True), []

        if tool_name == "write_file":
            path = str(arguments.get("path", "")).strip()
            content = str(arguments.get("content", ""))
            if not path:
                raise ValueError("write_file requires a path")
            saved = save_generated_file(path, content)
            return json.dumps({"saved": path}, ensure_ascii=True), [path]

        if tool_name == "run_python":
            code = str(arguments.get("code", ""))
            input_paths = arguments.get("input_paths", [])
            if not code.strip():
                raise ValueError("run_python requires code")
            if not isinstance(input_paths, list) or not all(isinstance(item, str) for item in input_paths):
                raise ValueError("input_paths must be a list of strings")
            result = self.sandbox.run(code, input_paths=input_paths)
            self._record_execution_result(session_id, tool_name, result)
            created = [str(item) for item in result.get("output_files", [])]
            return json.dumps(result, ensure_ascii=True), created

        if tool_name == "run_python_script":
            path = str(arguments.get("path", "")).strip()
            input_paths = arguments.get("input_paths", [])
            if not path:
                raise ValueError("run_python_script requires a path")
            if not isinstance(input_paths, list) or not all(isinstance(item, str) for item in input_paths):
                raise ValueError("input_paths must be a list of strings")
            result = self.sandbox.run_script(path, input_paths=input_paths)
            self._record_execution_result(session_id, tool_name, result)
            created = [str(item) for item in result.get("output_files", [])]
            return json.dumps(result, ensure_ascii=True), created

        if tool_name == "index_project":
            status = self.indexer.index_project()
            return json.dumps(status, ensure_ascii=True), []

        if tool_name == "search_project":
            query = str(arguments.get("query", "")).strip()
            limit = int(arguments.get("limit", 8))
            if not query:
                raise ValueError("search_project requires a query")
            results = self.indexer.search(query, limit=limit)
            return json.dumps({"results": results}, ensure_ascii=True), []

        if tool_name == "read_project_file":
            path = str(arguments.get("path", "")).strip()
            if not path:
                raise ValueError("read_project_file requires a path")
            result = self.indexer.read_project_file(path)
            return json.dumps(result, ensure_ascii=True), []

        if tool_name == "inspect_graph":
            query = str(arguments.get("query", "")).strip()
            limit = int(arguments.get("limit", 8))
            if not query:
                raise ValueError("inspect_graph requires a query")
            result = self.indexer.inspect_graph(query, limit=limit)
            return json.dumps(result, ensure_ascii=True), []

        raise ValueError(f"Unsupported tool: {tool_name}")

    def _record_execution_result(self, session_id: str, tool_name: str, result: dict[str, Any]) -> None:
        status = str(result.get("status", "")).strip()
        error_type = str(result.get("error_type", "")).strip() or None
        if status == "denied":
            reason = str(result.get("policy_reason") or result.get("user_message") or "Execution denied")
            self.sessions.record_execution_denied(session_id, reason)
            self._log_observation(
                session_id,
                "execution_denied",
                {"tool_name": tool_name, "error_type": error_type, "reason": reason},
            )
        elif status in {"failed", "timed_out"}:
            reason = str(result.get("stderr") or result.get("user_message") or "Execution failed")
            self.sessions.record_execution_error(session_id, reason)
            self._log_observation(
                session_id,
                "execution_error",
                {
                    "tool_name": tool_name,
                    "status": status,
                    "error_type": error_type,
                    "reason_preview": reason[:400],
                },
            )

    def _extract_files(self, reply: str) -> tuple[str, list[str]]:
        created_paths = []
        for match in FILE_BLOCK_RE.finditer(reply):
            relative_path = match.group("path").strip()
            content = match.group("content")
            saved = save_generated_file(relative_path, content)
            created_paths.append(saved)

        cleaned_reply = FILE_BLOCK_RE.sub("", reply).strip()
        return cleaned_reply, summarize_paths(created_paths)

    def _run_tool_loop(
        self,
        model: str,
        initial_prompt: str,
        session_id: str,
        max_steps: int = 4,
        initial_created_files: list[str] | None = None,
        initial_tool_events: list[str] | None = None,
        start_step: int = 0,
        transcript: list[dict[str, str]] | None = None,
    ) -> dict[str, Any]:
        base_prompt = initial_prompt
        tool_events: list[str] = list(initial_tool_events or [])
        created_files: list[str] = list(initial_created_files or [])
        tool_transcript: list[dict[str, str]] = list(transcript or [])
        final_reply = ""

        for step in range(start_step, max_steps):
            prompt = self._build_followup_prompt(base_prompt, tool_transcript)
            raw_reply = self._ollama_generate(model, prompt)
            tool_call = self._parse_tool_call(raw_reply, session_id)
            if not tool_call:
                cleaned_reply, extracted_files = self._extract_files(raw_reply)
                created_files.extend(extracted_files)
                final_reply = cleaned_reply or raw_reply
                break

            tool_name = tool_call["tool_name"]
            arguments = tool_call["arguments"]
            tool_events.append(f"Tool call: {tool_name} {json.dumps(arguments, ensure_ascii=True)}")

            if self.approval.requires_approval(tool_name):
                approval_request = self.approval.create(
                    model=model,
                    prompt=base_prompt,
                    raw_reply=raw_reply,
                    tool_name=tool_name,
                    arguments=arguments,
                    created_files=created_files,
                    tool_events=tool_events,
                    next_step=step + 1,
                    session_id=session_id,
                )
                self.sessions.record_approval(session_id)
                self._log_observation(
                    session_id,
                    "approval_requested",
                    {"tool_name": tool_name, "arguments": arguments},
                )
                final_reply = "Approval needed before I can continue with that action."
                return {
                    "reply": final_reply,
                    "created_files": created_files,
                    "tool_events": tool_events,
                    "requires_approval": True,
                    "approval_request": approval_request,
                }

            try:
                tool_result, new_files = self._execute_tool(tool_name, arguments, session_id)
            except Exception as exc:
                tool_result = json.dumps({"error": str(exc)}, ensure_ascii=True)
                new_files = []
                self.sessions.record_execution_error(session_id, str(exc))
                self._log_observation(
                    session_id,
                    "tool_execution_exception",
                    {"tool_name": tool_name, "reason": str(exc)},
                )

            created_files.extend(new_files)
            tool_events.append(f"Tool result: {tool_result[:400]}")
            tool_transcript.append(
                {
                    "tool_call": raw_reply.strip()[:2000],
                    "tool_result": tool_result[:4000],
                }
            )
        else:
            final_reply = "I stopped after the maximum number of tool steps. Please refine the request."

        return {
            "reply": final_reply,
            "created_files": created_files,
            "tool_events": tool_events,
            "requires_approval": False,
            "approval_request": None,
        }

    def chat(self, message: str, model: str, session_id: str) -> dict[str, Any]:
        with self.sessions.request_scope(session_id):
            prompt, lessons = self._build_prompt(message)
            loop_result = self._run_tool_loop(model, prompt, session_id=session_id)
            cleaned_reply = loop_result["reply"]
            created_files = loop_result["created_files"]
            tool_events = loop_result["tool_events"]
            updates, figured_out, ideas = self._build_progress_notes(created_files, tool_events)

            append_json_row(
                RUN_LOG_FILE,
                {
                    "timestamp": datetime.now(UTC).isoformat(),
                    "session_id": session_id,
                    "message": message,
                    "model": model,
                    "created_files": created_files,
                    "tool_events": tool_events,
                    "updates": updates,
                    "figured_out": figured_out,
                    "ideas": ideas,
                    "reply_preview": cleaned_reply[:400],
                },
            )

            if created_files:
                append_json_row(
                    LESSONS_FILE,
                    {
                        "timestamp": datetime.now(UTC).isoformat(),
                        "lesson": "When the user asks for a deliverable, provide both a concise explanation and a file output block.",
                    },
                )

            return {
                "reply": cleaned_reply,
                "created_files": created_files,
                "lessons_used": lessons,
                "tool_events": tool_events,
                "updates": updates,
                "figured_out": figured_out,
                "ideas": ideas,
                "trust_report": self._build_trust_report(
                    cleaned_reply,
                    created_files,
                    tool_events,
                    updates,
                    figured_out,
                    ideas,
                    loop_result["requires_approval"],
                ),
                "requires_approval": loop_result["requires_approval"],
                "approval_request": loop_result["approval_request"],
                "session": self.sessions.snapshot(session_id),
            }

    def generate_file(self, instruction: str, filename: str, model: str, session_id: str) -> dict[str, Any]:
        with self.sessions.request_scope(session_id):
            prompt = (
                f"{SYSTEM_PROMPT.strip()}\n\n"
                f"{self._tool_spec()}\n\n"
                f"Create exactly one file using this path:\n"
                f"<<<FILE:{filename}>>>\n"
                f"...content here...\n"
                f"<<<END FILE>>>\n\n"
                f"Instruction:\n{instruction.strip()}\n"
            )
            loop_result = self._run_tool_loop(model, prompt, session_id=session_id)
            cleaned_reply = loop_result["reply"]
            created_files = loop_result["created_files"]
            tool_events = loop_result["tool_events"]
            updates, figured_out, ideas = self._build_progress_notes(created_files, tool_events)
            return {
                "reply": cleaned_reply or "File generated.",
                "created_files": created_files,
                "lessons_used": load_recent_lessons(),
                "tool_events": tool_events,
                "updates": updates,
                "figured_out": figured_out,
                "ideas": ideas,
                "trust_report": self._build_trust_report(
                    cleaned_reply or "File generated.",
                    created_files,
                    tool_events,
                    updates,
                    figured_out,
                    ideas,
                    loop_result["requires_approval"],
                ),
                "requires_approval": loop_result["requires_approval"],
                "approval_request": loop_result["approval_request"],
                "session": self.sessions.snapshot(session_id),
            }

    def get_pending_approval(self, session_id: str) -> dict[str, Any] | None:
        return self.approval.current(session_id)

    def get_session_snapshot(self, session_id: str) -> dict[str, Any]:
        return self.sessions.snapshot(session_id)

    def approve_pending(self, session_id: str) -> dict[str, Any]:
        with self.sessions.request_scope(session_id):
            pending = self.approval.take(session_id)
            if pending is None:
                return {
                    "reply": "No approval request is waiting.",
                    "created_files": [],
                    "lessons_used": load_recent_lessons(),
                    "tool_events": [],
                    "updates": ["There was no pending action to approve."],
                    "figured_out": [],
                    "ideas": [],
                    "trust_report": {},
                    "requires_approval": False,
                    "approval_request": None,
                    "session": self.sessions.snapshot(session_id),
                }

            tool_events = list(pending.tool_events)
            created_files = list(pending.created_files)
            try:
                tool_result, new_files = self._execute_tool(pending.tool_name, pending.arguments, session_id)
            except Exception as exc:
                tool_result = json.dumps({"error": str(exc)}, ensure_ascii=True)
                new_files = []
                self.sessions.record_execution_error(session_id, str(exc))
                self._log_observation(session_id, "approved_tool_exception", {"tool_name": pending.tool_name, "reason": str(exc)})

            created_files.extend(new_files)
            tool_events.append(f"Approved tool result: {tool_result[:400]}")

            transcript = [{"tool_call": pending.raw_reply.strip()[:2000], "tool_result": tool_result[:4000]}]
            loop_result = self._run_tool_loop(
                model=pending.model,
                initial_prompt=pending.prompt,
                session_id=session_id,
                initial_created_files=created_files,
                initial_tool_events=tool_events,
                start_step=pending.next_step,
                transcript=transcript,
            )
            updates, figured_out, ideas = self._build_progress_notes(loop_result["created_files"], loop_result["tool_events"])
            return {
                "reply": loop_result["reply"],
                "created_files": loop_result["created_files"],
                "lessons_used": load_recent_lessons(),
                "tool_events": loop_result["tool_events"],
                "updates": updates,
                "figured_out": figured_out,
                "ideas": ideas,
                "trust_report": self._build_trust_report(
                    loop_result["reply"],
                    loop_result["created_files"],
                    loop_result["tool_events"],
                    updates,
                    figured_out,
                    ideas,
                    loop_result["requires_approval"],
                ),
                "requires_approval": loop_result["requires_approval"],
                "approval_request": loop_result["approval_request"],
                "session": self.sessions.snapshot(session_id),
            }

    def reject_pending(self, session_id: str) -> dict[str, Any]:
        with self.sessions.request_scope(session_id):
            pending = self.approval.take(session_id)
            if pending is None:
                return {
                    "reply": "No approval request is waiting.",
                    "created_files": [],
                    "lessons_used": load_recent_lessons(),
                    "tool_events": [],
                    "updates": ["There was no pending action to reject."],
                    "figured_out": [],
                    "ideas": [],
                    "trust_report": {},
                    "requires_approval": False,
                    "approval_request": None,
                    "session": self.sessions.snapshot(session_id),
                }

            updates = ["Rejected a pending risky action before it ran."]
            figured_out: list[str] = []
            ideas = ["You can revise the request or ask the agent for a safer alternative."]
            return {
                "reply": f"Rejected `{pending.tool_name}`. The agent stopped before executing it.",
                "created_files": pending.created_files,
                "lessons_used": load_recent_lessons(),
                "tool_events": pending.tool_events,
                "updates": updates,
                "figured_out": figured_out,
                "ideas": ideas,
                "trust_report": self._build_trust_report(
                    f"Rejected `{pending.tool_name}`. The agent stopped before executing it.",
                    pending.created_files,
                    pending.tool_events,
                    updates,
                    figured_out,
                    ideas,
                    False,
                ),
                "requires_approval": False,
                "approval_request": None,
                "session": self.sessions.snapshot(session_id),
            }

    def record_feedback(self, feedback: str, successful: bool) -> None:
        prefix = "Successful pattern:" if successful else "Avoid this issue:"
        append_json_row(
            LESSONS_FILE,
            {
                "timestamp": datetime.now(UTC).isoformat(),
                "lesson": f"{prefix} {feedback.strip()}",
            },
        )
