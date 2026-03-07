from __future__ import annotations

import io
import unittest
from contextlib import redirect_stdout
from dataclasses import dataclass
from unittest.mock import patch

from ingestion import __main__ as cli


@dataclass
class _FakeResult:
    file_path: str
    start_line: int
    end_line: int
    score: float
    text: str


class IngestionCliTests(unittest.TestCase):
    def test_ingest_json_output(self) -> None:
        with patch.object(cli, "KnowledgeQueryService") as service_cls:
            service_cls.return_value.refresh.return_value = {"status": "updated", "chunks_indexed": 4}
            with patch("sys.argv", ["python -m ingestion", "ingest", "--json"]):
                output = io.StringIO()
                with redirect_stdout(output):
                    cli.main()

        text = output.getvalue()
        self.assertIn('"status": "updated"', text)
        self.assertIn('"chunks_indexed": 4', text)

    def test_ask_text_output(self) -> None:
        with patch.object(cli, "KnowledgeQueryService") as service_cls:
            service_cls.return_value.query.return_value = [
                _FakeResult("README.md", 1, 8, 0.99, "sample text")
            ]
            with patch("sys.argv", ["python -m ingestion", "ask", "what is this", "--top-k", "1"]):
                output = io.StringIO()
                with redirect_stdout(output):
                    cli.main()

        text = output.getvalue()
        self.assertIn("README.md:1-8", text)
        self.assertIn("sample text", text)

    def test_auto_ingest_respects_cycle_limit(self) -> None:
        service = type("Service", (), {"refresh": lambda self: {"status": "unchanged"}})()
        sleeps: list[float] = []
        output = io.StringIO()

        with redirect_stdout(output):
            cli.run_auto_ingest(
                service=service,
                interval_seconds=0.5,
                max_cycles=2,
                as_json=False,
                sleep_fn=lambda seconds: sleeps.append(seconds),
            )

        text = output.getvalue()
        self.assertIn("Auto-ingest cycle 1", text)
        self.assertIn("Auto-ingest cycle 2", text)
        self.assertEqual(sleeps, [0.5])


if __name__ == "__main__":
    unittest.main()
