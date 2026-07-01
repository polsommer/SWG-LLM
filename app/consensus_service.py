from __future__ import annotations

from pathlib import Path
from typing import Any, Callable

from consensus.debate_engine import DebateEngine, Evidence
from consensus.refinement_loop import RefinementLoop

from .git_publisher import GitPublisher
from .storage import GENERATED_DIR


class ConsensusService:
    def __init__(
        self,
        indexer: Any,
        generate_text: Callable[[str, str], str],
        git_publisher: GitPublisher | None = None,
    ) -> None:
        self.indexer = indexer
        self.generate_text = generate_text
        self.git_publisher = git_publisher or GitPublisher()
        self.output_dir = GENERATED_DIR / "consensus"
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def _retriever(self, question: str, top_k: int) -> list[dict[str, object]]:
        results = self.indexer.search(question, limit=top_k)
        rows: list[dict[str, object]] = []
        for index, item in enumerate(results):
            rows.append(
                {
                    "chunk_id": str(item.get("chunk_id") or f"consensus-{index}"),
                    "score": float(item.get("score", 0.0)),
                    "file_path": str(item.get("path", "")),
                    "start_line": 1,
                    "end_line": 1,
                    "text": str(item.get("snippet", "")),
                    "semantic_score": float(item.get("semantic_score", 0.0)),
                    "keyword_score": float(item.get("lexical_score", 0.0)),
                    "rerank_score": float(item.get("score", 0.0)),
                    "document_title": Path(str(item.get("path", "unknown"))).name or "unknown",
                    "section": "retrieved_chunk",
                    "last_updated": "unknown",
                    "access_scope": "workspace",
                    "source_kind": "code",
                }
            )
        return rows

    def _answer_generator(self, model: str) -> Callable[[str, tuple[Evidence, ...], tuple[Evidence, ...], int], str]:
        def _generate(prompt: str, own_evidence: tuple[Evidence, ...], peer_evidence: tuple[Evidence, ...], round_index: int) -> str:
            evidence_lines = [
                f"- {item.file_path}:{item.start_line}-{item.end_line} | {item.excerpt[:220].replace(chr(10), ' ')}"
                for item in own_evidence[:4]
            ]
            peer_lines = [
                f"- {item.file_path}:{item.start_line}-{item.end_line}"
                for item in peer_evidence[:3]
            ]
            consensus_prompt = (
                "You are producing one side of a grounded SWG consensus answer.\n"
                f"Round: {round_index}\n"
                f"Question: {prompt}\n\n"
                "Your evidence:\n"
                f"{chr(10).join(evidence_lines) or '- No evidence'}\n\n"
                "Peer evidence headings:\n"
                f"{chr(10).join(peer_lines) or '- No peer evidence'}\n\n"
                "Write a concise grounded answer that only relies on the evidence above. If evidence is weak, say so clearly."
            )
            return self.generate_text(model, consensus_prompt)

        return _generate

    def run(
        self,
        *,
        prompt: str,
        model: str,
        filename: str,
        top_k: int = 5,
        commit_to_git: bool = False,
        push_to_remote: bool = False,
        commit_message: str = "Add consensus artifact",
    ) -> dict[str, Any]:
        artifact_name = Path(filename).name
        engine = DebateEngine(
            self._retriever,
            self._retriever,
            answer_a=self._answer_generator(model),
            answer_b=self._answer_generator(model),
            workdir=self.output_dir,
        )
        loop = RefinementLoop(debate_engine=engine)
        result = loop.run_and_persist(prompt=prompt, top_k=top_k, filename=artifact_name)
        artifact_path = self.output_dir / artifact_name

        git_result = None
        if commit_to_git:
            git_result = self.git_publisher.publish_files(
                paths=[artifact_path],
                commit_message=commit_message,
                push_to_remote=push_to_remote,
            )

        return {
            "artifact_path": artifact_path.resolve(),
            "artifact_relative_path": artifact_path.resolve().relative_to(GENERATED_DIR.resolve()).as_posix(),
            "conclusion": result.conclusion.conclusion,
            "confidence": result.conclusion.confidence,
            "agreement_score": result.conclusion.agreement_score,
            "rounds_used": result.rounds_used,
            "grounded": result.conclusion.grounded,
            "git": git_result,
        }
