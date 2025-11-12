"""Command line interface for SWG-LLM."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

from .agent import SWGAssistant
from .config import SWGConfig


def _build_config(args: argparse.Namespace) -> SWGConfig:
    repo_root = Path(args.repository).resolve()
    index_dir = Path(args.index_dir or repo_root / ".swg_llm").resolve()
    return SWGConfig(
        repository_root=repo_root,
        index_dir=index_dir,
        embedding_model=args.embedding_model,
        generator_model=args.generator_model,
        include_globs=tuple(args.include or ()),
        exclude_globs=tuple(args.exclude or ()),
        chunk_size=args.chunk_size,
        chunk_overlap=args.chunk_overlap,
        top_k=args.top_k,
        cpu_only=args.cpu_only,
    )


def handle_index(args: argparse.Namespace) -> None:
    config = _build_config(args)
    assistant = SWGAssistant(config)
    count = assistant.build_index()
    print(f"Indexed {count} chunks into {config.index_dir}")


def handle_query(args: argparse.Namespace) -> None:
    config = _build_config(args)
    assistant = SWGAssistant(config)
    assistant.load_index()
    result = assistant.answer_question(args.question)
    print("Answer:\n" + result.context)
    print("\nSources:")
    for source in result.sources:
        print(f"- {source['source_path']} (lines {source['start_line']}-{source['end_line']})")


def handle_patch(args: argparse.Namespace) -> None:
    config = _build_config(args)
    assistant = SWGAssistant(config)
    assistant.load_index()
    file_path = Path(args.file).resolve()
    suggestion = assistant.suggest_patch(file_path, args.instructions, args.question)
    print(suggestion)
    if args.apply:
        assistant.apply_patch(file_path, suggestion)
        print(f"Applied patch to {file_path}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="SWG repository LLM assistant")
    parser.add_argument("repository", help="Path to repository to index")
    parser.add_argument("--index-dir", help="Directory where embeddings will be stored")
    parser.add_argument("--embedding-model", default="sentence-transformers/all-MiniLM-L6-v2")
    parser.add_argument("--generator-model", default="microsoft/phi-2")
    parser.add_argument(
        "--cpu-only",
        action="store_true",
        help="Force generation to run on CPU with memory-saving defaults",
    )
    parser.add_argument("--include", nargs="*", help="Glob patterns to include")
    parser.add_argument("--exclude", nargs="*", help="Glob patterns to exclude")
    parser.add_argument("--chunk-size", type=int, default=1200)
    parser.add_argument("--chunk-overlap", type=int, default=120)
    parser.add_argument("--top-k", type=int, default=4)

    subparsers = parser.add_subparsers(dest="command", required=True)

    index_parser = subparsers.add_parser("index", help="Build the repository index")
    index_parser.set_defaults(func=handle_index)

    query_parser = subparsers.add_parser("query", help="Ask the assistant a question")
    query_parser.add_argument("question")
    query_parser.set_defaults(func=handle_query)

    patch_parser = subparsers.add_parser("patch", help="Generate a patch suggestion")
    patch_parser.add_argument("file")
    patch_parser.add_argument("instructions")
    patch_parser.add_argument("--question")
    patch_parser.add_argument("--apply", action="store_true")
    patch_parser.set_defaults(func=handle_patch)

    return parser


def main(argv: list[str] | None = None) -> Any:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":  # pragma: no cover
    main()
