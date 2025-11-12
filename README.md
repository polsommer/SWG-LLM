# SWG-LLM

SWG-LLM is a lightweight Retrieval-Augmented Generation (RAG) assistant that
indexes the SWG repository, answers questions about the codebase, and proposes
patches that you can review or apply locally. The tool is designed to run
entirely on your workstation using open-source Hugging Face models.

## Features

- **Repository ingestion** – Chunk source files with configurable include and
  exclude patterns.
- **Vector search** – Compute embeddings with `sentence-transformers` and store
  them in a local JSON index.
- **Question answering** – Retrieve the most relevant code snippets and forward
  them to a local text-generation model for contextual responses.
- **Patch suggestions** – Generate unified diffs for targeted files and apply
  them in place after manual review.

## Installation

1. Ensure you have Python 3.10 or newer.
2. Install the project in editable mode (consider using a virtual environment):

   ```bash
   pip install -e .
   ```

   The dependencies include PyTorch and Transformers. Depending on the model you
   choose, installation may require additional system libraries (e.g., CUDA).
   For strictly CPU environments, install the CPU build of PyTorch and enable
   the `--cpu-only` flag described below.

## Usage

The CLI entrypoint is exposed as `swg-llm`. Each command requires the path to
the repository you want the assistant to learn.

### Build the index

```bash
swg-llm /path/to/repo index --index-dir /path/to/repo/.swg_llm
```

- `--include` / `--exclude` allow fine-grained control over which files are
  processed.
- `--chunk-size` and `--chunk-overlap` adjust how source files are split prior
  to embedding.

### Ask a question

```bash
swg-llm /path/to/repo query "How does authentication work?"
```

The assistant retrieves the most relevant chunks, passes them to the generator,
and prints both the answer and the source locations for traceability.

### Generate a patch suggestion

```bash
swg-llm /path/to/repo patch path/to/file.py "Add input validation" --apply
```

This command retrieves context, requests a patch proposal, prints it to stdout,
and applies a naïve replacement that reuses added lines from the diff. Review
and test changes before committing them.

## Configuration

Adjust default models and behavior through CLI flags or by instantiating
`SWGConfig` directly in Python code. For advanced workflows, import the
`SWGAssistant` class and orchestrate ingestion, retrieval, and generation in your
own scripts or notebooks.

### CPU-only mode

Pass `--cpu-only` to force the generator pipeline to stay on CPU with
memory-saving defaults—ideal for Raspberry Pi or other low-resource machines.
Combine this with a lightweight model such as `distilgpt2` or
`tiiuae/falcon-rw-1b` to keep resource usage manageable:

```bash
swg-llm /path/to/repo query "What does the scheduler do?" \
  --generator-model distilgpt2 --cpu-only
```

## Safety and limitations

- The default generator (`microsoft/phi-2`) requires significant memory (GPU
  recommended). Swap it for a smaller model via `--generator-model` if needed.
- Patch application is intentionally conservative and only supports simple
  replacements. Prefer manual review before applying suggestions.
- The assistant relies entirely on the local index—remember to rebuild the index
  after large refactors.
