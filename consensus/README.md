# consensus

Debate/refinement and disagreement resolution stage.

Implemented submodules:
- `conflict_detector.py` - claim extraction, semantic overlap, contradiction scoring.
- `debate_engine.py` - node response generation, evidence exchange wiring, merged conclusion persistence.
- `refinement_loop.py` - iterative convergence loop controlled by disagreement and round thresholds.

Typical flow:
1. Node `192.168.88.5` and Node `192.168.88.10` independently generate answers from ingestion evidence.
2. `ConflictDetector` computes claim-level semantic alignment + contradiction score.
3. `RefinementLoop` triggers additional rounds when disagreement exceeds threshold.
4. Run stops when agreement threshold is met or max rounds is reached.
5. Before final merge, `DebateEngine` builds retrieval context under a strict token budget and enforces grounded output (or abstains when evidence is missing).
6. `DebateEngine.persist_conclusion` stores merged conclusion, confidence, provenance, and grounded status.
7. Retrieval usage telemetry is appended to `retrieval_usage.jsonl` to track hit/miss behavior.
