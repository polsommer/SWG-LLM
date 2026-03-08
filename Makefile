.PHONY: ingest ask auto-ingest web-chat

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


web-chat:
	SWG_WEB_HOST="$${SWG_WEB_HOST:-192.168.88.10}" SWG_WEB_PORT="$${SWG_WEB_PORT:-8080}" SWG_LLM_BACKEND="$${SWG_LLM_BACKEND:-mock}" SWG_WORKDIR="$${SWG_WORKDIR:-$(CURDIR)/.swg-workdir}" python -m webapp
