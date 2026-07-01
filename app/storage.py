from __future__ import annotations

import json
from pathlib import Path
from typing import Iterable


BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
UPLOADS_DIR = DATA_DIR / "uploads"
GENERATED_DIR = DATA_DIR / "generated"
MEMORY_DIR = DATA_DIR / "memory"
SANDBOXES_DIR = DATA_DIR / "sandboxes"
PROJECT_INDEX_DIR = DATA_DIR / "project_index"
LESSONS_FILE = MEMORY_DIR / "lessons.json"
KNOWLEDGE_FILE = MEMORY_DIR / "knowledge.json"
RUN_LOG_FILE = MEMORY_DIR / "runs.json"
OBSERVABILITY_FILE = MEMORY_DIR / "observability.json"
INTELLIGENCE_FILE = MEMORY_DIR / "intelligence.json"
COUNCIL_FILE = MEMORY_DIR / "council.json"
WORKSPACE_LEARNING_FILE = MEMORY_DIR / "workspace_learning.json"
PROPOSALS_FILE = MEMORY_DIR / "proposals.json"
AUTOPILOT_FILE = MEMORY_DIR / "autopilot.json"
SWG_MAIN_DIR = BASE_DIR / "swg-main"
PROJECT_ROOTS = [SWG_MAIN_DIR / "src", SWG_MAIN_DIR / "dsrc"]
PROJECT_SETTINGS_FILE = DATA_DIR / "project_settings.json"


def ensure_dirs() -> None:
    for path in (UPLOADS_DIR, GENERATED_DIR, MEMORY_DIR, SANDBOXES_DIR, PROJECT_INDEX_DIR):
        path.mkdir(parents=True, exist_ok=True)
    for file_path in (LESSONS_FILE, KNOWLEDGE_FILE, RUN_LOG_FILE, OBSERVABILITY_FILE):
        if not file_path.exists():
            file_path.write_text("[]", encoding="utf-8")
    if not INTELLIGENCE_FILE.exists():
        INTELLIGENCE_FILE.write_text(
            json.dumps(
                {
                    "last_run_at": None,
                    "source_indexed_at": None,
                    "status": "idle",
                    "briefing": [],
                    "focus_areas": [],
                    "suggested_tasks": [],
                    "repo_hypotheses": [],
                    "signals": [],
                },
                indent=2,
                ensure_ascii=True,
            ),
            encoding="utf-8",
        )
    if not COUNCIL_FILE.exists():
        COUNCIL_FILE.write_text(
            json.dumps(
                {
                    "settings": {
                        "enabled": True,
                        "auto_commit_enabled": False,
                        "auto_push_enabled": False,
                        "poll_seconds": 45,
                        "test_command": 'py -m unittest discover -s tests -p "test_*.py"',
                        "model": "qwen2.5:7b-instruct-q4_K_M",
                        "auto_approve_threshold": 2,
                    },
                    "state": "idle",
                    "last_run_at": None,
                    "last_signature": "",
                    "last_error": None,
                    "git": {},
                    "tests": {},
                    "decision": {},
                    "transcript": [],
                    "votes": [],
                },
                indent=2,
                ensure_ascii=True,
            ),
            encoding="utf-8",
        )
    if not WORKSPACE_LEARNING_FILE.exists():
        WORKSPACE_LEARNING_FILE.write_text(
            json.dumps(
                {
                    "settings": {
                        "enabled": True,
                        "poll_seconds": 25,
                        "model": "qwen2.5:7b-instruct-q4_K_M",
                    },
                    "state": "idle",
                    "last_run_at": None,
                    "last_signature": "",
                    "last_error": None,
                    "recent_items": [],
                },
                indent=2,
                ensure_ascii=True,
            ),
            encoding="utf-8",
        )
    if not PROPOSALS_FILE.exists():
        PROPOSALS_FILE.write_text(
            json.dumps(
                {
                    "last_run_at": None,
                    "active_proposal": None,
                    "recent_proposals": [],
                },
                indent=2,
                ensure_ascii=True,
            ),
            encoding="utf-8",
        )
    if not AUTOPILOT_FILE.exists():
        AUTOPILOT_FILE.write_text(
            json.dumps(
                {
                    "settings": {
                        "enabled": True,
                        "poll_seconds": 90,
                        "model": "qwen2.5:7b-instruct-q4_K_M",
                        "max_changed_lines": 240,
                        "max_changed_files": 3,
                    },
                    "state": "idle",
                    "last_run_at": None,
                    "last_signature": "",
                    "last_error": None,
                    "active_proposal": None,
                    "plan": {},
                    "execution": {},
                    "safety": {},
                },
                indent=2,
                ensure_ascii=True,
            ),
            encoding="utf-8",
        )
    if not PROJECT_SETTINGS_FILE.exists():
        PROJECT_SETTINGS_FILE.write_text(
            json.dumps(
                {
                    "project_roots": [str(path) for path in PROJECT_ROOTS],
                },
                indent=2,
                ensure_ascii=True,
            ),
            encoding="utf-8",
        )


def load_json_list(path: Path) -> list[dict]:
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, list) else []
    except json.JSONDecodeError:
        return []


def save_json_list(path: Path, rows: list[dict]) -> None:
    path.write_text(json.dumps(rows, indent=2, ensure_ascii=True), encoding="utf-8")


def load_project_settings() -> dict:
    if not PROJECT_SETTINGS_FILE.exists():
        return {"project_roots": [str(path) for path in PROJECT_ROOTS]}
    try:
        data = json.loads(PROJECT_SETTINGS_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            roots = data.get("project_roots")
            if isinstance(roots, list):
                cleaned = [str(item).strip() for item in roots if str(item).strip()]
                return {"project_roots": cleaned or [str(path) for path in PROJECT_ROOTS]}
    except json.JSONDecodeError:
        pass
    return {"project_roots": [str(path) for path in PROJECT_ROOTS]}


def save_project_settings(settings: dict) -> dict:
    cleaned = {
        "project_roots": [str(item).strip() for item in settings.get("project_roots", []) if str(item).strip()],
    }
    if not cleaned["project_roots"]:
        cleaned["project_roots"] = [str(path) for path in PROJECT_ROOTS]
    PROJECT_SETTINGS_FILE.write_text(json.dumps(cleaned, indent=2, ensure_ascii=True), encoding="utf-8")
    return cleaned


def append_json_row(path: Path, row: dict) -> None:
    rows = load_json_list(path)
    rows.append(row)
    save_json_list(path, rows)


def list_text_files(root: Path) -> list[Path]:
    allowed = {
        ".txt",
        ".md",
        ".py",
        ".js",
        ".ts",
        ".tsx",
        ".jsx",
        ".json",
        ".yaml",
        ".yml",
        ".html",
        ".css",
        ".csv",
        ".xml",
        ".ini",
        ".log",
    }
    return [p for p in root.rglob("*") if p.is_file() and p.suffix.lower() in allowed]


def read_snippet(path: Path, max_chars: int = 2500) -> str:
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""
    text = text.strip()
    if len(text) <= max_chars:
        return text
    return text[:max_chars] + "\n...[truncated]"


def read_text_file(path: Path, max_chars: int | None = None) -> str:
    try:
        with path.open("r", encoding="utf-8", errors="ignore") as handle:
            return handle.read() if max_chars is None else handle.read(max_chars)
    except OSError:
        return ""


def collect_workspace_context(limit: int = 6) -> list[dict]:
    files = list_text_files(UPLOADS_DIR) + list_text_files(GENERATED_DIR)
    items: list[dict] = []
    for file_path in files[:limit]:
        try:
            relative = file_path.relative_to(DATA_DIR).as_posix()
        except ValueError:
            relative = file_path.name
        items.append(
            {
                "path": relative,
                "snippet": read_snippet(file_path),
            }
        )
    return items


def load_recent_lessons(limit: int = 5) -> list[str]:
    lessons = load_json_list(LESSONS_FILE)
    values = [row.get("lesson", "").strip() for row in lessons if row.get("lesson")]
    return values[-limit:]


def load_recent_knowledge(limit: int = 8) -> list[dict]:
    rows = load_json_list(KNOWLEDGE_FILE)
    cleaned: list[dict] = []
    for row in rows[-limit:]:
        summary = str(row.get("summary", "")).strip()
        if not summary:
            continue
        cleaned.append(
            {
                "timestamp": row.get("timestamp"),
                "summary": summary,
                "kind": str(row.get("kind", "observation")).strip() or "observation",
            }
        )
    return cleaned


def load_intelligence_snapshot() -> dict:
    if not INTELLIGENCE_FILE.exists():
        return {
            "last_run_at": None,
            "source_indexed_at": None,
            "status": "idle",
            "briefing": [],
            "focus_areas": [],
            "suggested_tasks": [],
            "repo_hypotheses": [],
            "signals": [],
        }
    try:
        data = json.loads(INTELLIGENCE_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            return data
    except json.JSONDecodeError:
        pass
    return {
        "last_run_at": None,
        "source_indexed_at": None,
        "status": "idle",
        "briefing": [],
        "focus_areas": [],
        "suggested_tasks": [],
        "repo_hypotheses": [],
        "signals": [],
    }


def save_intelligence_snapshot(snapshot: dict) -> dict:
    INTELLIGENCE_FILE.write_text(json.dumps(snapshot, indent=2, ensure_ascii=True), encoding="utf-8")
    return snapshot


def load_council_snapshot() -> dict:
    if not COUNCIL_FILE.exists():
        return {
            "settings": {
                "enabled": True,
                "auto_commit_enabled": False,
                "auto_push_enabled": False,
                "poll_seconds": 45,
                "test_command": 'py -m unittest discover -s tests -p "test_*.py"',
                "model": "qwen2.5:7b-instruct-q4_K_M",
                "auto_approve_threshold": 2,
            },
            "state": "idle",
            "last_run_at": None,
            "last_signature": "",
            "last_error": None,
            "git": {},
            "tests": {},
            "decision": {},
            "transcript": [],
            "votes": [],
        }
    try:
        data = json.loads(COUNCIL_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            return data
    except json.JSONDecodeError:
        pass
    return {
        "settings": {
            "enabled": True,
            "auto_commit_enabled": False,
            "auto_push_enabled": False,
            "poll_seconds": 45,
            "test_command": 'py -m unittest discover -s tests -p "test_*.py"',
            "model": "qwen2.5:7b-instruct-q4_K_M",
            "auto_approve_threshold": 2,
        },
        "state": "idle",
        "last_run_at": None,
        "last_signature": "",
        "last_error": None,
        "git": {},
        "tests": {},
        "decision": {},
        "transcript": [],
        "votes": [],
    }


def save_council_snapshot(snapshot: dict) -> dict:
    COUNCIL_FILE.write_text(json.dumps(snapshot, indent=2, ensure_ascii=True), encoding="utf-8")
    return snapshot


def load_workspace_learning_snapshot() -> dict:
    if not WORKSPACE_LEARNING_FILE.exists():
        return {
            "settings": {
                "enabled": True,
                "poll_seconds": 25,
                "model": "qwen2.5:7b-instruct-q4_K_M",
            },
            "state": "idle",
            "last_run_at": None,
            "last_signature": "",
            "last_error": None,
            "recent_items": [],
        }
    try:
        data = json.loads(WORKSPACE_LEARNING_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            return data
    except json.JSONDecodeError:
        pass
    return {
        "settings": {
            "enabled": True,
            "poll_seconds": 25,
            "model": "qwen2.5:7b-instruct-q4_K_M",
        },
        "state": "idle",
        "last_run_at": None,
        "last_signature": "",
        "last_error": None,
        "recent_items": [],
    }


def save_workspace_learning_snapshot(snapshot: dict) -> dict:
    WORKSPACE_LEARNING_FILE.write_text(json.dumps(snapshot, indent=2, ensure_ascii=True), encoding="utf-8")
    return snapshot


def load_proposals_snapshot() -> dict:
    if not PROPOSALS_FILE.exists():
        return {
            "last_run_at": None,
            "active_proposal": None,
            "recent_proposals": [],
        }
    try:
        data = json.loads(PROPOSALS_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            return data
    except json.JSONDecodeError:
        pass
    return {
        "last_run_at": None,
        "active_proposal": None,
        "recent_proposals": [],
    }


def save_proposals_snapshot(snapshot: dict) -> dict:
    PROPOSALS_FILE.write_text(json.dumps(snapshot, indent=2, ensure_ascii=True), encoding="utf-8")
    return snapshot


def load_autopilot_snapshot() -> dict:
    if not AUTOPILOT_FILE.exists():
        return {
            "settings": {
                "enabled": True,
                "poll_seconds": 90,
                "model": "qwen2.5:7b-instruct-q4_K_M",
                "max_changed_lines": 240,
                "max_changed_files": 3,
            },
            "state": "idle",
            "last_run_at": None,
            "last_signature": "",
            "last_error": None,
            "active_proposal": None,
            "plan": {},
            "execution": {},
            "safety": {},
        }
    try:
        data = json.loads(AUTOPILOT_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict):
            return data
    except json.JSONDecodeError:
        pass
    return {
        "settings": {
            "enabled": True,
            "poll_seconds": 90,
            "model": "qwen2.5:7b-instruct-q4_K_M",
            "max_changed_lines": 240,
            "max_changed_files": 3,
        },
        "state": "idle",
        "last_run_at": None,
        "last_signature": "",
        "last_error": None,
        "active_proposal": None,
        "plan": {},
        "execution": {},
        "safety": {},
    }


def save_autopilot_snapshot(snapshot: dict) -> dict:
    AUTOPILOT_FILE.write_text(json.dumps(snapshot, indent=2, ensure_ascii=True), encoding="utf-8")
    return snapshot


def merge_proposals(source: str, proposals: list[dict]) -> dict:
    snapshot = load_proposals_snapshot()
    current = snapshot.get("recent_proposals", [])
    by_id: dict[str, dict] = {}
    if isinstance(current, list):
        for item in current:
            if isinstance(item, dict) and str(item.get("id", "")).strip():
                by_id[str(item["id"])] = dict(item)

    for item in proposals:
        if not isinstance(item, dict):
            continue
        proposal_id = str(item.get("id", "")).strip()
        if not proposal_id:
            continue
        merged = dict(by_id.get(proposal_id, {}))
        merged.update(item)
        merged["source"] = source
        merged["status"] = str(merged.get("status", "proposed")).strip() or "proposed"
        by_id[proposal_id] = merged

    rows = list(by_id.values())
    rows.sort(
        key=lambda item: (
            0 if str(item.get("priority", "")).lower() == "high" else 1,
            -float(item.get("confidence", 0.0) or 0.0),
            str(item.get("updated_at") or item.get("created_at") or ""),
        ),
        reverse=False,
    )
    snapshot["recent_proposals"] = rows[:18]
    snapshot["active_proposal"] = next(
        (item for item in snapshot["recent_proposals"] if str(item.get("status", "proposed")) in {"proposed", "planned", "executed"}),
        snapshot["recent_proposals"][0] if snapshot["recent_proposals"] else None,
    )
    snapshot["last_run_at"] = datetime_now_iso()
    return save_proposals_snapshot(snapshot)


def datetime_now_iso() -> str:
    from datetime import UTC, datetime

    return datetime.now(UTC).isoformat()


def memory_stats() -> dict:
    intelligence = load_intelligence_snapshot()
    workspace_learning = load_workspace_learning_snapshot()
    proposals = load_proposals_snapshot()
    autopilot = load_autopilot_snapshot()
    return {
        "lesson_count": len(load_json_list(LESSONS_FILE)),
        "knowledge_count": len(load_json_list(KNOWLEDGE_FILE)),
        "run_count": len(load_json_list(RUN_LOG_FILE)),
        "recent_knowledge": load_recent_knowledge(),
        "recent_lessons": load_recent_lessons(),
        "automation_last_run_at": intelligence.get("last_run_at"),
        "automation_focus_count": len(intelligence.get("focus_areas", [])),
        "workspace_learning_last_run_at": workspace_learning.get("last_run_at"),
        "workspace_learning_count": len(workspace_learning.get("recent_items", [])),
        "proposal_count": len(proposals.get("recent_proposals", [])),
        "autopilot_last_run_at": autopilot.get("last_run_at"),
    }


def save_uploaded_file(name: str, content: bytes) -> Path:
    safe_name = Path(name).name
    target = UPLOADS_DIR / safe_name
    target.write_bytes(content)
    return target


def resolve_workspace_file(location: str) -> Path:
    normalized = location.strip().replace("\\", "/")
    if normalized.startswith("uploads/"):
        base = UPLOADS_DIR.resolve()
        relative = normalized.removeprefix("uploads/")
    elif normalized.startswith("generated/"):
        base = GENERATED_DIR.resolve()
        relative = normalized.removeprefix("generated/")
    else:
        raise ValueError("Path must start with uploads/ or generated/")

    target = (base / relative).resolve()
    if not str(target).startswith(str(base)):
        raise ValueError("Refusing to access outside workspace area")
    return target


def save_generated_file(relative_path: str, content: str) -> Path:
    target = (GENERATED_DIR / relative_path).resolve()
    base = GENERATED_DIR.resolve()
    if not str(target).startswith(str(base)):
        raise ValueError("Refusing to write outside generated directory")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    return target


def save_generated_bytes(relative_path: str, content: bytes) -> Path:
    target = (GENERATED_DIR / relative_path).resolve()
    base = GENERATED_DIR.resolve()
    if not str(target).startswith(str(base)):
        raise ValueError("Refusing to write outside generated directory")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(content)
    return target


def summarize_paths(paths: Iterable[Path]) -> list[str]:
    base = GENERATED_DIR.resolve()
    items: list[str] = []
    for path in paths:
        try:
            items.append(path.resolve().relative_to(base).as_posix())
        except ValueError:
            items.append(path.name)
    return items
