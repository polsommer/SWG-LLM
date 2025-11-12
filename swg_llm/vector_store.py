"""In-memory vector store used for retrieval augmented generation."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Sequence, Tuple

import numpy as np

from .repository import DocumentChunk


@dataclass(slots=True)
class VectorStoreDocument:
    """Embedding and metadata for a repository chunk."""

    embedding: np.ndarray
    metadata: dict
    text: str

    def as_serializable(self) -> dict:
        return {
            "embedding": self.embedding.astype(np.float32).tolist(),
            "metadata": self.metadata,
            "text": self.text,
        }

    @staticmethod
    def from_serializable(payload: dict) -> "VectorStoreDocument":
        return VectorStoreDocument(
            embedding=np.array(payload["embedding"], dtype=np.float32),
            metadata=payload["metadata"],
            text=payload["text"],
        )


class LocalVectorStore:
    """Simple vector store backed by a JSON file."""

    def __init__(self) -> None:
        self._documents: List[VectorStoreDocument] = []

    @property
    def documents(self) -> Sequence[VectorStoreDocument]:
        return self._documents

    def add_embeddings(self, chunks: Iterable[DocumentChunk], embeddings: np.ndarray) -> None:
        for chunk, embedding in zip(chunks, embeddings):
            self._documents.append(
                VectorStoreDocument(
                    embedding=np.asarray(embedding, dtype=np.float32),
                    metadata=chunk.to_metadata(),
                    text=chunk.text,
                )
            )

    def search(self, query_vector: np.ndarray, k: int) -> List[VectorStoreDocument]:
        """Return the top-k documents by cosine similarity."""

        if not self._documents:
            return []
        matrix = np.vstack([doc.embedding for doc in self._documents])
        query = np.asarray(query_vector, dtype=np.float32)
        similarities = matrix @ query / (
            np.linalg.norm(matrix, axis=1) * np.linalg.norm(query) + 1e-8
        )
        top_indices = np.argsort(similarities)[-k:][::-1]
        return [self._documents[idx] for idx in top_indices]

    def save(self, path: Path) -> None:
        payload = [doc.as_serializable() for doc in self._documents]
        path.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    @classmethod
    def load(cls, path: Path) -> "LocalVectorStore":
        store = cls()
        if not path.exists():
            return store
        data = json.loads(path.read_text(encoding="utf-8"))
        store._documents = [VectorStoreDocument.from_serializable(item) for item in data]
        return store

    def __len__(self) -> int:  # pragma: no cover - trivial wrapper
        return len(self._documents)

    def __iter__(self):  # pragma: no cover - convenience
        return iter(self._documents)
