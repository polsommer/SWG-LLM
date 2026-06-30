from __future__ import annotations

import traceback
from pathlib import Path

import uvicorn


if __name__ == "__main__":
    log_path = Path(__file__).resolve().parent / "work_uvicorn_err.log"
    try:
        uvicorn.run(
            "app.main:app",
            host="127.0.0.1",
            port=8000,
            log_level="warning",
            access_log=False,
            log_config=None,
        )
    except Exception:
        log_path.write_text(traceback.format_exc(), encoding="utf-8")
        raise
