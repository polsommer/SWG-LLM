from __future__ import annotations

import json
import threading
import unittest
from unittest.mock import patch
from urllib import request

from ingestion.knowledge_store import QueryResult
from webapp.server import WebAppConfig, _build_chat_payload, load_config_from_env, create_server


class _FakeService:
    def query(self, question: str, top_k: int = 5) -> list[QueryResult]:
        return [
            QueryResult(
                chunk_id="c1",
                score=1.0,
                file_path="README.md",
                start_line=1,
                end_line=2,
                text=f"{question}-answer",
                semantic_score=1.0,
                keyword_score=0.5,
                rerank_score=0.9,
                document_title="doc",
                section="root",
                last_updated="now",
                access_scope="restricted",
                source_kind="code",
            )
        ][:top_k]




class _EmptyService:
    def query(self, question: str, top_k: int = 5) -> list[QueryResult]:
        return []


class WebAppServerTests(unittest.TestCase):
    def test_build_chat_payload_includes_answer_citations_and_metadata(self) -> None:
        payload = _build_chat_payload(_FakeService(), "hello", top_k=1, max_context_chars=5)
        self.assertIn("answer", payload)
        self.assertIn("citations", payload)
        self.assertIn("metadata", payload)
        self.assertEqual(payload["metadata"]["top_k"], 1)
        self.assertEqual(len(payload["citations"]), 1)
        self.assertIn("[truncated]", payload["citations"][0]["snippet"])


    def test_build_chat_payload_abstains_without_retrieval(self) -> None:
        payload = _build_chat_payload(_EmptyService(), "unknown", top_k=2, max_context_chars=50)
        self.assertIn("not enough evidence", payload["answer"].lower())
        self.assertEqual(payload["citations"], [])

    def test_load_config_from_env_defaults(self) -> None:
        with patch.dict("os.environ", {}, clear=True):
            config = load_config_from_env()

        self.assertEqual(config.host, "192.168.88.10")
        self.assertEqual(config.port, 8080)
        self.assertEqual(config.default_top_k, 5)
        self.assertEqual(config.max_context_chars, 4000)

    def test_http_endpoints(self) -> None:
        service = _FakeService()
        server = create_server(service, config=WebAppConfig(host="127.0.0.1", port=0, default_top_k=5, max_context_chars=100))
        thread = threading.Thread(target=server.serve_forever)
        thread.start()

        try:
            host, port = server.server_address
            health = request.urlopen(f"http://{host}:{port}/healthz")
            health_payload = json.loads(health.read().decode("utf-8"))
            self.assertEqual(health_payload, {"ok": True})

            chat_req = request.Request(
                url=f"http://{host}:{port}/api/chat",
                data=json.dumps({"message": "hello", "top_k": 1}).encode("utf-8"),
                method="POST",
                headers={"Content-Type": "application/json"},
            )
            chat = request.urlopen(chat_req)
            chat_payload = json.loads(chat.read().decode("utf-8"))
            self.assertIn("answer", chat_payload)
            self.assertEqual(chat_payload["metadata"]["top_k"], 1)
            self.assertEqual(len(chat_payload["citations"]), 1)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
