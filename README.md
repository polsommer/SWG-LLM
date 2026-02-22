# SWG-LLM

## Setup

```bash
./gradlew test
```

## Ingest repository artifacts

```bash
./gradlew run --args='--mode ingest --repo-path ../swg-main'
```

## Run REPL chat

```bash
./gradlew run --args='chat'
```

Useful commands in chat:
- `/help` command reference
- `/reset` clear conversation history
- `/context` inspect active context usage
- `/source` print retrieval citations for the latest answer
- `/exit` leave chat

Multiline prompts are supported by ending your input with a line containing only `.`.

## Runtime profiles

Profiles live in `src/main/resources/application.yml`:
- `cpu-low-memory`: optimized for low-memory CPU inference.
- `intel-igpu`: OpenVINO/oneDNN iGPU path; automatically falls back to `cpu-low-memory` when unavailable.

To choose a profile:

```bash
./gradlew run --args='chat --runtime-profile intel-igpu'
```

## Adaptive defaults for Core i5 + 18GB RAM target

Defaults are tuned for M900x-class systems:
- Quantized model: `phi-3-mini-4k-instruct-q4`
- Context window cap: 2048 tokens on CPU, 3072 on Intel iGPU
- Retrieval chunk cap: bounded to 4 (CPU) / 5 (iGPU) chunks to keep latency predictable

## Telemetry

Each answer logs runtime telemetry including:
- tokens/sec
- first-token latency (ms)
- process memory usage (MB)
- active execution backend (`cpu` or `intel-igpu`)

## Expected performance ranges (Lenovo M900x-class guidance)

Typical ranges for quantized local inference with bounded retrieval:
- CPU profile: ~18-35 tokens/sec, first token ~120-280ms
- Intel iGPU profile (OpenVINO path): ~28-55 tokens/sec, first token ~80-180ms

Actual throughput depends on model artifact, drivers, JVM settings, and retrieval index size.
