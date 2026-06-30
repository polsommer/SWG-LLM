from __future__ import annotations

import ast
import json
import shutil
import subprocess
import sys
from datetime import datetime, UTC
from pathlib import Path
from typing import Any

from .storage import SANDBOXES_DIR, read_text_file, resolve_workspace_file, save_generated_bytes


class GuardedExecutionDenied(ValueError):
    def __init__(self, reason: str) -> None:
        super().__init__(reason)
        self.reason = reason


class PythonSandbox:
    def __init__(self, timeout_seconds: int = 8) -> None:
        self.timeout_seconds = timeout_seconds
        self.allowed_modules = {
            "collections",
            "csv",
            "datetime",
            "decimal",
            "fractions",
            "itertools",
            "json",
            "math",
            "random",
            "re",
            "statistics",
            "string",
        }
        self.blocked_modules = {
            "asyncio",
            "ctypes",
            "importlib",
            "multiprocessing",
            "os",
            "pathlib",
            "pickle",
            "shutil",
            "socket",
            "subprocess",
            "sys",
            "threading",
        }
        self.blocked_calls = {
            "__import__",
            "compile",
            "eval",
            "exec",
            "getattr",
            "globals",
            "input",
            "locals",
            "setattr",
            "vars",
        }
        self.blocked_attributes = {
            "fork",
            "forkserver",
            "kill",
            "popen",
            "remove",
            "removedirs",
            "rename",
            "replace",
            "rmdir",
            "run",
            "socket",
            "spawn",
            "startfile",
            "system",
            "unlink",
        }

    def _make_run_dir(self) -> Path:
        run_id = datetime.now(UTC).strftime("run_%Y%m%dT%H%M%S_%fZ")
        run_dir = SANDBOXES_DIR / run_id
        run_dir.mkdir(parents=True, exist_ok=False)
        (run_dir / "input").mkdir()
        (run_dir / "output").mkdir()
        return run_dir

    def _copy_inputs(self, run_dir: Path, input_paths: list[str]) -> list[str]:
        copied: list[str] = []
        input_root = run_dir / "input"
        for item in input_paths:
            source = resolve_workspace_file(item)
            if not source.exists() or not source.is_file():
                raise ValueError(f"Input file not found: {item}")
            target = (input_root / Path(item).name).resolve()
            if not str(target).startswith(str(input_root.resolve())):
                raise ValueError("Refusing to copy outside sandbox input directory")
            shutil.copy2(source, target)
            copied.append(target.name)
        return copied

    def _build_wrapper(self, code: str) -> str:
        payload = json.dumps(code)
        return f"""
import builtins
import json
import os
import pathlib
import sys

ALLOWED_WRITES = pathlib.Path("output").resolve()
ALLOWED_READS = {{
    pathlib.Path(".").resolve(),
    pathlib.Path("input").resolve(),
    pathlib.Path("output").resolve(),
}}
ALLOWED_MODULES = {sorted(self.allowed_modules)!r}

_real_open = builtins.open
_real_import = builtins.__import__

def _safe_open(file, mode="r", *args, **kwargs):
    path = pathlib.Path(file).resolve()
    wants_write = any(flag in mode for flag in ("w", "a", "x", "+"))
    if wants_write:
        if not str(path).startswith(str(ALLOWED_WRITES)):
            raise PermissionError("Sandbox writes are limited to the output/ directory")
    else:
        if not any(str(path).startswith(str(root)) for root in ALLOWED_READS):
            raise PermissionError("Sandbox reads are limited to the run directory")
    return _real_open(file, mode, *args, **kwargs)

def _safe_import(name, globals=None, locals=None, fromlist=(), level=0):
    root = name.split(".", 1)[0]
    if root not in ALLOWED_MODULES:
        raise ImportError(f"Import '{{name}}' is not allowed in this guarded runner")
    return _real_import(name, globals, locals, fromlist, level)

builtins.open = _safe_open
builtins.__import__ = _safe_import
os.environ.clear()
os.environ["PYTHONNOUSERSITE"] = "1"

code = json.loads({payload!r})
globals_dict = {{
    "__name__": "__main__",
    "__file__": "agent_task.py",
}}
exec(compile(code, "agent_task.py", "exec"), globals_dict, globals_dict)
"""

    def _validate_code(self, code: str) -> None:
        try:
            tree = ast.parse(code, mode="exec")
        except SyntaxError as exc:
            raise ValueError(f"Python syntax error: {exc.msg}") from exc

        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    root = alias.name.split(".", 1)[0]
                    if root in self.blocked_modules or root not in self.allowed_modules:
                        raise GuardedExecutionDenied(f"Import '{alias.name}' is not allowed in the guarded runner")
            elif isinstance(node, ast.ImportFrom):
                module = (node.module or "").split(".", 1)[0]
                if node.level != 0 or module in self.blocked_modules or module not in self.allowed_modules:
                    raise GuardedExecutionDenied(f"Import from '{node.module or ''}' is not allowed in the guarded runner")
            elif isinstance(node, ast.Call):
                func = node.func
                if isinstance(func, ast.Name) and func.id in self.blocked_calls:
                    raise GuardedExecutionDenied(f"Call to '{func.id}' is not allowed in the guarded runner")
                if isinstance(func, ast.Attribute) and func.attr in self.blocked_attributes:
                    raise GuardedExecutionDenied(f"Attribute call '{func.attr}' is not allowed in the guarded runner")
            elif isinstance(node, ast.Attribute) and node.attr in {"__dict__", "__class__", "__globals__", "__subclasses__"}:
                raise GuardedExecutionDenied(f"Attribute '{node.attr}' is not allowed in the guarded runner")

    def _collect_outputs(self, run_dir: Path) -> list[str]:
        output_root = run_dir / "output"
        run_name = run_dir.name
        saved: list[str] = []
        for item in output_root.rglob("*"):
            if not item.is_file():
                continue
            relative = item.relative_to(output_root).as_posix()
            target = f"sandbox_runs/{run_name}/{relative}"
            save_generated_bytes(target, item.read_bytes())
            saved.append(target)
        return saved

    def run(self, code: str, input_paths: list[str] | None = None) -> dict[str, Any]:
        try:
            self._validate_code(code)
        except GuardedExecutionDenied as exc:
            return {
                "status": "denied",
                "error_type": "policy_denied",
                "user_message": "Execution was blocked by the guarded runner policy before it started.",
                "policy_reason": exc.reason,
                "timeout_seconds": self.timeout_seconds,
                "output_files": [],
            }
        run_dir = self._make_run_dir()
        copied_inputs = self._copy_inputs(run_dir, input_paths or [])
        wrapper_path = run_dir / "__sandbox_main__.py"
        wrapper_path.write_text(self._build_wrapper(code), encoding="utf-8")

        env = {
            "PYTHONNOUSERSITE": "1",
            "PYTHONPATH": "",
            "PIP_DISABLE_PIP_VERSION_CHECK": "1",
            "HTTP_PROXY": "",
            "HTTPS_PROXY": "",
            "ALL_PROXY": "",
            "NO_PROXY": "*",
        }

        try:
            completed = subprocess.run(
                [sys.executable, "-I", str(wrapper_path)],
                cwd=run_dir,
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
                env=env,
            )
            timed_out = False
        except subprocess.TimeoutExpired as exc:
            completed = exc
            timed_out = True

        if timed_out:
            stdout = (completed.stdout or "")[:8000]
            stderr = "Execution timed out."
            return_code = -1
        else:
            stdout = (completed.stdout or "")[:8000]
            stderr = (completed.stderr or "")[:8000]
            return_code = completed.returncode

        output_files = self._collect_outputs(run_dir)
        status = "completed"
        error_type: str | None = None
        user_message = "Execution completed."
        if timed_out:
            status = "timed_out"
            error_type = "timeout"
            user_message = "Execution timed out before completion."
        elif return_code != 0:
            if "not allowed in this guarded runner" in stderr:
                status = "denied"
                error_type = "policy_denied"
                user_message = "Execution was blocked by the guarded runner policy while running."
            else:
                status = "failed"
                error_type = "runtime_error"
                user_message = "Execution started but failed at runtime."
        summary = {
            "status": status,
            "error_type": error_type,
            "user_message": user_message,
            "python": sys.executable,
            "timeout_seconds": self.timeout_seconds,
            "copied_inputs": copied_inputs,
            "return_code": return_code,
            "stdout": stdout,
            "stderr": stderr,
            "output_files": output_files,
            "run_dir": run_dir.name,
        }
        return summary

    def run_script(self, script_path: str, input_paths: list[str] | None = None) -> dict[str, Any]:
        source = resolve_workspace_file(script_path)
        if source.suffix.lower() != ".py":
            raise ValueError("run_python_script only supports .py files")
        code = read_text_file(source)
        return self.run(code, input_paths=input_paths)
