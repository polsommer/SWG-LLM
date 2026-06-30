"""CLI helpers for repository ingestion and retrieval."""

from __future__ import annotations

import argparse
import json
import time
from dataclasses import asdict
from typing import Callable

from .query_interface import KnowledgeQueryService


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="python -m ingestion",
        description="Easy commands to ingest data and query the local SWG-LLM knowledge index.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    ingest = subparsers.add_parser("ingest", help="Sync source repo and refresh the local index.")
    ingest.add_argument(
        "--json",
        action="store_true",
        help="Print machine-readable JSON output.",
    )

    ask = subparsers.add_parser("ask", help="Query indexed knowledge with semantic retrieval.")
    ask.add_argument("question", help="Question to retrieve relevant context for.")
    ask.add_argument("--top-k", type=int, default=5, help="How many results to return.")
    ask.add_argument(
        "--json",
        action="store_true",
        help="Print machine-readable JSON output.",
    )

    auto = subparsers.add_parser(
        "auto-ingest",
        help="Continuously pull updates and refresh the local index on an interval.",
    )
    auto.add_argument(
        "--interval-seconds",
        type=float,
        default=60.0,
        help="Seconds to wait between refresh cycles.",
    )
    auto.add_argument(
        "--max-cycles",
        type=int,
        default=0,
        help="Optional cycle limit; 0 means run forever.",
    )
    auto.add_argument(
        "--json",
        action="store_true",
        help="Print machine-readable JSON output.",
    )
    return parser


def _print_ingest(summary: dict[str, object], as_json: bool) -> None:
    if as_json:
        print(json.dumps(summary, indent=2, sort_keys=True))
        return

    print("Ingestion summary:")
    for key, value in summary.items():
        print(f"- {key}: {value}")


def _print_query(results: list[object], as_json: bool) -> None:
    if as_json:
        print(json.dumps([asdict(item) for item in results], indent=2))
        return

    if not results:
        print("No results found.")
        return

    for i, result in enumerate(results, 1):
        print(f"\n[{i}] {result.file_path}:{result.start_line}-{result.end_line} score={result.score:.4f}")
        print(result.text)


def run_auto_ingest(
    service: KnowledgeQueryService,
    interval_seconds: float,
    max_cycles: int,
    as_json: bool,
    sleep_fn: Callable[[float], None] = time.sleep,
) -> None:
    if interval_seconds <= 0:
        raise ValueError("interval_seconds must be > 0")

    cycle = 0
    while max_cycles == 0 or cycle < max_cycles:
        cycle += 1
        summary = service.refresh()

        if as_json:
            print(json.dumps({"cycle": cycle, "summary": summary}, indent=2, sort_keys=True))
        else:
            print(f"Auto-ingest cycle {cycle}:")
            for key, value in summary.items():
                print(f"- {key}: {value}")

        if max_cycles != 0 and cycle >= max_cycles:
            break
        sleep_fn(interval_seconds)


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    service = KnowledgeQueryService()

    if args.command == "ingest":
        _print_ingest(service.refresh(), as_json=args.json)
        return

    if args.command == "ask":
        _print_query(service.query(args.question, top_k=args.top_k), as_json=args.json)
        return

    if args.command == "auto-ingest":
        run_auto_ingest(
            service=service,
            interval_seconds=args.interval_seconds,
            max_cycles=args.max_cycles,
            as_json=args.json,
        )
        return

    parser.error(f"Unknown command: {args.command}")


if __name__ == "__main__":
    main()
