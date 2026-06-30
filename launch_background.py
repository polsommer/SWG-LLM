from __future__ import annotations

import subprocess
import sys
from pathlib import Path


DETACHED_PROCESS = 0x00000008
CREATE_NEW_PROCESS_GROUP = 0x00000200


def main() -> int:
    project_root = Path(__file__).resolve().parent
    pythonw = Path(sys.executable).with_name("pythonw.exe")
    python = pythonw if pythonw.exists() else Path(sys.executable)
    out_log = project_root / "work_uvicorn_out.log"
    err_log = project_root / "work_uvicorn_err.log"

    out_handle = out_log.open("w", encoding="utf-8")
    err_handle = err_log.open("w", encoding="utf-8")

    try:
        process = subprocess.Popen(
            [
                str(python),
                str(project_root / "serve_local_agent.py"),
            ],
            cwd=project_root,
            stdin=subprocess.DEVNULL,
            stdout=out_handle,
            stderr=err_handle,
            creationflags=DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP,
            close_fds=True,
        )
    finally:
        out_handle.close()
        err_handle.close()

    print(process.pid)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
