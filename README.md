# SWG-LLM

Local Java/Gradle command-line LLM assistant for SWG workflows, with repository ingestion, retrieval-grounded chat, runtime profiles, governance checks, and an offline improvement pipeline.

## Install guide (fresh machine)

This setup is written so you can copy/paste commands and get to a working chat quickly.

### 1) Prerequisites

- **OS**: Linux/macOS (Windows works with `gradlew.bat`)
- **JDK**: Java **21** (recommended)
- **Git**: recent version

Check your tools:

```bash
java -version
git --version
```

If Java is missing or too old, install JDK 21 first.

### 1.1) Install Gradle (optional)

You can run this project with the included Gradle Wrapper (`./gradlew`) and do **not** need a system-wide Gradle install.
If you still want Gradle installed globally:

- **macOS (Homebrew)**

  ```bash
  brew install gradle
  ```

- **Ubuntu/Debian**

  ```bash
  sudo apt update
  sudo apt install -y gradle
  ```

Verify:

```bash
gradle -v
```

### 2) Clone the repo

```bash
git clone https://github.com/polsommer/SWG-LLM.git
cd SWG-LLM
```

### 3) Verify the project builds

```bash
./gradlew clean test
```

If this passes, your local install is healthy.

### 4) (Optional but recommended) Ingest your SWG code/content repo

Point SWG-LLM to a repository you want to query in chat. You can provide a local path (example: `../swg-main`) or a remote URL:

```bash
./gradlew run --args='--mode ingest --repo-path ../swg-main'
./gradlew run --args='--mode ingest --repo-url https://github.com/SWG-Source/dsrc.git'
```

When `--repo-url` is provided, SWG-LLM clones/updates it under `.swgllm/repos` by default before ingestion.

### 5) Start chat

```bash
./gradlew run --args='chat --repo-path ../swg-main'
```

If you do not need retrieval context yet, you can also run:

```bash
./gradlew run --args='chat'
```

---

## “Just works” quick start (copy/paste)

```bash
git clone https://github.com/polsommer/SWG-LLM.git
cd SWG-LLM
./gradlew clean test
./gradlew run --args='--mode ingest --repo-url https://github.com/SWG-Source/dsrc.git'
./gradlew run --args='chat --runtime-profile intel-igpu'
./gradlew run --args='--mode retrieve --query "Where is the login flow defined?"'
```

If `intel-igpu` is not available, SWG-LLM automatically falls back to `cpu-low-memory`.

## Mode selection and prompt quality guide

### When to use each mode

| Mode | Use it when | Typical command |
|---|---|---|
| `--mode chat` | You want conversational answers grounded in repo context. | `./gradlew run --args='chat --repo-path ../swg-main'` |
| `--mode retrieve` | You want raw evidence lookup without full answer synthesis. | `./gradlew run --args='--mode retrieve --query "Where is login flow defined?"'` |
| `--mode ingest` | You are doing first-time indexing, or the target repo was updated and needs re-indexing. | `./gradlew run --args='--mode ingest --repo-path ../swg-main'` |
| `--mode daemon` | You want continuous ingest + improve cycles running in the background. | `./gradlew run --args='--mode daemon --repo-path ../swg-main'` |

### Prompt patterns that produce better answers

Use patterns like these to increase precision and usefulness:

- `Where is X defined? Return file path + function/class.`
- `Explain this in beginner terms, then advanced terms.`
- `Summarize with bullet points and cite top 3 sources.`

### Bad vs good prompt examples (SWG domain)

- **Login flow**
  - Bad: `Explain login.`
  - Good: `Where is the login flow defined? Return file path + function/class, and include citations.`
- **Buildout**
  - Bad: `What about buildout stuff?`
  - Good: `Summarize buildout initialization in bullet points and cite top 3 sources.`
- **Spawning**
  - Bad: `How does spawning work?`
  - Good: `Explain spawning in beginner terms, then advanced terms, and name the key symbols involved.`

### Mini troubleshooting for noisy retrieval

If results are broad or noisy:

- Narrow query terms to specific mechanics or objects (for example, `player login session handoff` instead of `login`).
- Include subsystem keywords (for example, `server`, `buildout`, `spawning`, `zone`, `auth`).
- Explicitly request symbol names and citations in the response.

### Verification habit

After every important response, run `/source` in chat to verify the cited evidence before acting on the answer.

## Chat commands

Inside the chat REPL:

- `/help` command reference
- `/reset` clear conversation history
- `/context` inspect active context usage
- `/source` print retrieval citations for the latest answer
- `/improve` trigger offline self-improvement pipeline immediately
- `/exit` leave chat

Multiline prompts are supported by ending input with a line containing only `.`.

## Runtime profiles

Profiles are defined in `src/main/resources/application.yml`:

- `cpu-low-memory`: optimized for low-memory CPU inference.
- `intel-igpu`: OpenVINO/oneDNN iGPU path; automatically falls back to CPU when unavailable.

Choose a profile explicitly:

```bash
./gradlew run --args='chat --runtime-profile intel-igpu'
```

## Adaptive defaults (Core i5 + 18GB RAM target)

Defaults are tuned for M900x-class systems:

- Quantized model: `phi-3-mini-4k-instruct-q4`
- Context window cap: 2048 tokens on CPU, 3072 on Intel iGPU
- Retrieval chunk cap: 4 chunks (CPU) / 5 chunks (iGPU) to keep latency predictable

## Telemetry output

Each answer logs runtime telemetry including:

- tokens/sec
- first-token latency (ms)
- process memory usage (MB)
- active backend (`cpu` or `intel-igpu`)

## Expected performance ranges (Lenovo M900x-class guidance)

Typical ranges for quantized local inference with bounded retrieval:

- CPU profile: ~18-35 tokens/sec, first token ~120-280ms
- Intel iGPU profile (OpenVINO path): ~28-55 tokens/sec, first token ~80-180ms

Actual throughput depends on model artifact, drivers, JVM settings, and retrieval index size.

## Offline improvement pipeline

Chat turns can auto-capture approved feedback (enabled by default) to:

- `.swgllm/feedback-log.json`

Run the improvement pipeline at any time:

```bash
./gradlew run --args='--mode improve'
```

Disable automatic chat learning capture when needed:

```bash
./gradlew run --args='chat --enable-auto-learn=false'
```

## Troubleshooting

### `./gradlew` is not executable

```bash
chmod +x gradlew
```

### Intel iGPU profile does not activate

- Confirm Intel GPU/OpenVINO runtime and drivers are installed.
- Try launching anyway; SWG-LLM falls back to CPU automatically.

### Build/test failures after dependency or JDK changes

```bash
./gradlew --stop
./gradlew clean test
```
