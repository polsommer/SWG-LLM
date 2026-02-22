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

## SWG-focused local assistant workflow (Ubuntu 22.04, Lenovo ThinkCentre M900x)

This project already ships a full Java + Gradle CLI workflow for SWG knowledge tasks.

### 1) Build and verify

```bash
./gradlew clean test
```

### 2) Ingest SWG source knowledge (from your local clone)

```bash
./gradlew run --args='--mode ingest --repo-path ../swg-main'
```

### 3) Chat with retrieval grounding

```bash
./gradlew run --args='chat --runtime-profile intel-igpu --repo-path ../swg-main'
```

Inside chat:
- ask normal prompts about SWG systems/content
- `/source` to print cited snippets
- `/improve` to trigger offline self-improvement pipeline immediately

### 4) Continuous self-improvement loop

Every chat turn now auto-captures approved feedback (enabled by default) into `.swgllm/feedback-log.json`.
Run the pipeline any time:

```bash
./gradlew run --args='--mode improve'
```

Disable automatic chat learning capture when needed:

```bash
./gradlew run --args='chat --enable-auto-learn=false'
```

### Ubuntu 22.04 + Intel iGPU notes

- Use latest Intel GPU runtime/compute drivers supported on Ubuntu 22.04.
- Keep `intel-igpu` runtime profile selected for maximum throughput.
- On systems where iGPU acceleration is unavailable, runtime automatically falls back to `cpu-low-memory`.

