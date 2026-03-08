"""Minimal HTTP web app for SWG retrieval chat."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import time
from pathlib import Path
from typing import Any

from ingestion.query_interface import KnowledgeQueryService
from webapp.llm_adapter import (
    _DEFAULT_ABSTAIN_ANSWER,
    build_context,
    build_prompt,
    generate_answer,
    get_backend_name,
)


@dataclass(frozen=True)
class WebAppConfig:
    host: str = "192.168.88.10"
    port: int = 8080
    default_top_k: int = 5
    max_context_chars: int = 4000


def load_config_from_env() -> WebAppConfig:
    return WebAppConfig(
        host=os.getenv("SWG_WEB_HOST", "192.168.88.10"),
        port=_int_from_env("SWG_WEB_PORT", 8080),
        default_top_k=_int_from_env("SWG_CHAT_TOP_K", 5),
        max_context_chars=_int_from_env("SWG_CHAT_MAX_CHARS", 4000),
    )


def _int_from_env(name: str, default: int) -> int:
    value = os.getenv(name)
    if value is None:
        return default
    try:
        return int(value)
    except ValueError:
        return default


def _html_page() -> str:
    static_file = Path(__file__).parent / "static" / "index.html"
    if static_file.exists():
        return static_file.read_text(encoding="utf-8")
    return """<!doctype html>
<html>
<head><meta charset=\"utf-8\"><title>SWG Chat</title></head>
<body><h1>SWG Chat</h1></body>
</html>
"""


def _trim_context(text: str, max_chars: int) -> str:
    if len(text) <= max_chars:
        return text
    return f"{text[:max_chars]}\n...[truncated]"


def _build_chat_payload(
    service: KnowledgeQueryService,
    message: str,
    top_k: int,
    max_context_chars: int,
) -> dict[str, Any]:
    start = time.perf_counter()
    results = build_context(message, top_k=top_k, service=service)

    if not results:
        answer = _DEFAULT_ABSTAIN_ANSWER
    else:
        prompt = build_prompt(message, results)
        answer = generate_answer(prompt)

    citations = [
        {
            "file_path": item.file_path,
            "start_line": item.start_line,
            "end_line": item.end_line,
            "snippet": _trim_context(item.text, max_context_chars),
        }
        for item in results
    ]

    latency_ms = round((time.perf_counter() - start) * 1000, 2)

    return {
        "answer": answer,
        "citations": citations,
        "metadata": {
            "backend": get_backend_name(),
            "top_k": top_k,
            "latency_ms": latency_ms,
        },
        "results": [asdict(item) for item in results],
    }


class _WebHandler(BaseHTTPRequestHandler):
    service: KnowledgeQueryService
    config: WebAppConfig

    def log_message(self, format: str, *args: object) -> None:  # noqa: A003
        return

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/healthz":
            self._send_json(HTTPStatus.OK, {"ok": True})
            return

        if self.path == "/":
            self._send_html(HTTPStatus.OK, _html_page())
            return

        self._send_json(HTTPStatus.NOT_FOUND, {"error": "Not found"})

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/api/chat":
            self._send_json(HTTPStatus.NOT_FOUND, {"error": "Not found"})
            return

        payload = self._read_json_body()
        if payload is None:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": "Invalid JSON payload"})
            return

        message = payload.get("message")
        if not isinstance(message, str) or not message.strip():
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": "message is required"})
            return

        top_k = payload.get("top_k", self.config.default_top_k)
        if not isinstance(top_k, int):
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": "top_k must be an integer"})
            return

        response = _build_chat_payload(
            service=self.service,
            message=message,
            top_k=max(1, top_k),
            max_context_chars=max(1, self.config.max_context_chars),
        )
        self._send_json(HTTPStatus.OK, response)

    def _read_json_body(self) -> dict[str, Any] | None:
        length = self.headers.get("Content-Length")
        if not length:
            return None

        try:
            size = int(length)
        except ValueError:
            return None

        raw_body = self.rfile.read(size)
        try:
            data = json.loads(raw_body)
        except json.JSONDecodeError:
            return None

        if not isinstance(data, dict):
            return None
        return data

    def _send_json(self, status: HTTPStatus, payload: dict[str, Any]) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_html(self, status: HTTPStatus, payload: str) -> None:
        body = payload.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def create_server(
    service: KnowledgeQueryService,
    config: WebAppConfig | None = None,
) -> ThreadingHTTPServer:
    runtime_config = config or load_config_from_env()
    class Handler(_WebHandler):
        pass

    Handler.service = service
    Handler.config = runtime_config
    return ThreadingHTTPServer((runtime_config.host, runtime_config.port), Handler)


def run_server(service: KnowledgeQueryService | None = None, config: WebAppConfig | None = None) -> None:
    query_service = service or KnowledgeQueryService()
    server = create_server(query_service, config=config)
    print(f"Serving SWG web app on http://{server.server_address[0]}:{server.server_address[1]}")
    server.serve_forever()
