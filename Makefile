.PHONY: ingest ask auto-ingest

ingest:
	python -m ingestion ingest

auto-ingest:
	python -m ingestion auto-ingest --interval-seconds "${INTERVAL:-60}"

ask:
	@if [ -z "$(Q)" ]; then \
		echo "Usage: make ask Q='your question'"; \
		exit 1; \
	fi
	python -m ingestion ask "$(Q)"
