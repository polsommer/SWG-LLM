"""Core assistant orchestration."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Sequence

import numpy as np

try:  # pragma: no cover - optional dependencies
    from sentence_transformers import SentenceTransformer
except Exception as exc:  # pragma: no cover - fallback
    SentenceTransformer = None  # type: ignore[misc]

try:  # pragma: no cover - optional dependencies
    from transformers import AutoModelForCausalLM, AutoTokenizer, pipeline
except Exception:  # pragma: no cover - fallback
    AutoModelForCausalLM = None  # type: ignore[misc]
    AutoTokenizer = None  # type: ignore[misc]
    pipeline = None  # type: ignore[misc]

from .config import SWGConfig
from .repository import DocumentChunk, RepositoryLoader
from .vector_store import LocalVectorStore


@dataclass(slots=True)
class RetrievalResult:
    """Context retrieved for answering a question."""

    context: str
    sources: Sequence[dict]


class SWGAssistant:
    """High-level orchestrator tying together retrieval and generation."""

    def __init__(self, config: SWGConfig) -> None:
        self.config = config
        self.loader = RepositoryLoader(config)
        self.vector_store = LocalVectorStore()
        self._embedder = None
        self._generator = None

    # ------------------------------------------------------------------
    # Embedding helpers
    # ------------------------------------------------------------------
    def _load_embedder(self):
        if self._embedder is None:
            if SentenceTransformer is None:  # pragma: no cover - runtime guard
                raise RuntimeError(
                    "sentence-transformers is required but not installed."
                )
            self._embedder = SentenceTransformer(self.config.embedding_model)
        return self._embedder

    def embed_documents(self, chunks: Sequence[DocumentChunk]) -> np.ndarray:
        model = self._load_embedder()
        return np.asarray(model.encode([chunk.text for chunk in chunks], show_progress_bar=True))

    def embed_query(self, query: str) -> np.ndarray:
        model = self._load_embedder()
        return np.asarray(model.encode([query]))[0]

    # ------------------------------------------------------------------
    # Index management
    # ------------------------------------------------------------------
    def build_index(self) -> int:
        """Load repository, compute embeddings, and persist the index."""

        chunks = self.loader.load()
        embeddings = self.embed_documents(chunks)
        self.vector_store = LocalVectorStore()
        self.vector_store.add_embeddings(chunks, embeddings)
        index_path = self.config.ensure_index_dir() / "index.json"
        self.vector_store.save(index_path)
        return len(self.vector_store)

    def load_index(self) -> None:
        index_path = self.config.ensure_index_dir() / "index.json"
        self.vector_store = LocalVectorStore.load(index_path)

    # ------------------------------------------------------------------
    # Retrieval
    # ------------------------------------------------------------------
    def retrieve(self, query: str) -> RetrievalResult:
        if not len(self.vector_store):
            raise RuntimeError("Vector store is empty. Run build_index() first.")
        query_vector = self.embed_query(query)
        docs = self.vector_store.search(query_vector, self.config.top_k)
        context = "\n\n".join(doc.text for doc in docs)
        return RetrievalResult(context=context, sources=[doc.metadata for doc in docs])

    # ------------------------------------------------------------------
    # Generation
    # ------------------------------------------------------------------
    def _load_generator(self):
        if self._generator is None:
            if pipeline is None or AutoTokenizer is None or AutoModelForCausalLM is None:
                raise RuntimeError("transformers is required but not installed.")
            tokenizer = AutoTokenizer.from_pretrained(self.config.generator_model)
            model_kwargs = {}
            if self.config.cpu_only:
                model_kwargs.update(low_cpu_mem_usage=True)
            model = AutoModelForCausalLM.from_pretrained(
                self.config.generator_model,
                **model_kwargs,
            )
            if self.config.cpu_only:
                model = model.to("cpu")
            self._generator = pipeline(
                "text-generation",
                model=model,
                tokenizer=tokenizer,
                max_new_tokens=512,
                temperature=0.2,
                device=-1 if self.config.cpu_only else None,
            )
        return self._generator

    def generate(self, prompt: str) -> str:
        generator = self._load_generator()
        outputs = generator(prompt)
        return outputs[0]["generated_text"]

    def answer_question(self, question: str) -> RetrievalResult:
        retrieval = self.retrieve(question)
        prompt = self._build_prompt(question, retrieval.context)
        answer = self.generate(prompt)
        return RetrievalResult(context=answer, sources=retrieval.sources)

    def _build_prompt(self, question: str, context: str) -> str:
        system_prompt = (
            "You are the SWG repository assistant. Use the provided context to answer "
            "the question. If the context is insufficient, explain what is missing."
        )
        return f"{system_prompt}\n\nContext:\n{context}\n\nQuestion: {question}\nAnswer:"

    # ------------------------------------------------------------------
    # Guided editing
    # ------------------------------------------------------------------
    def suggest_patch(self, file_path: Path, instructions: str, question: str | None = None) -> str:
        """Generate a patch proposal based on the current file contents."""

        contents = file_path.read_text(encoding="utf-8")
        prompt = (
            "You are helping to update a repository file. Provided instructions describe the "
            "desired modification. Propose a unified diff patch."
            "\n\nFile path: {path}\n\nCurrent contents:\n{contents}\n\nInstructions:\n{instructions}\n"
        ).format(path=file_path, contents=contents)
        if question:
            prompt += f"\nRelated question: {question}\n"
        prompt += "\nRespond with a valid unified diff starting with 'diff --git'."
        return self.generate(prompt)

    def apply_patch(self, file_path: Path, patch_text: str) -> None:
        """Apply a simple patch that replaces file contents."""

        # For safety, only handle patches that include a complete replacement section.
        if "@@" not in patch_text:
            raise ValueError("Patch does not contain any hunks; refusing to apply.")
        # Naive strategy: find last line starting with '+', use as replacement body.
        new_lines: List[str] = []
        for line in patch_text.splitlines():
            if line.startswith("+") and not line.startswith("+++"):
                new_lines.append(line[1:])
        file_path.write_text("\n".join(new_lines), encoding="utf-8")


__all__ = ["SWGAssistant", "RetrievalResult"]
