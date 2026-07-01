from __future__ import annotations

from fastapi import FastAPI, File, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from .agent import LocalAgent
from .background_intelligence import BackgroundIntelligence
from .background_indexer import BackgroundIndexer
from .background_council import BackgroundCouncil
from .background_workspace_learning import BackgroundWorkspaceLearning
from .consensus_service import ConsensusService
from .indexer import ProjectIndexer
from .models import ChatRequest, ChatResponse, ConsensusRequest, CouncilSettingsRequest, FeedbackRequest, GenerateRequest, ProjectRootsUpdateRequest, TestRequest
from .storage import (
    BASE_DIR,
    GENERATED_DIR,
    UPLOADS_DIR,
    ensure_dirs,
    load_recent_lessons,
    memory_stats,
    save_uploaded_file,
)


ensure_dirs()

app = FastAPI(title="LocalAgent 1660")
agent = LocalAgent()
indexer = ProjectIndexer()
agent.indexer = indexer
background_indexer = BackgroundIndexer(indexer=indexer)
background_intelligence = BackgroundIntelligence(indexer=indexer)
background_workspace_learning = BackgroundWorkspaceLearning(generate_text=agent._ollama_generate)
consensus_service = ConsensusService(indexer=indexer, generate_text=agent._ollama_generate)
council = BackgroundCouncil(generate_text=agent._ollama_generate)
static_dir = BASE_DIR / "static"

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/static", StaticFiles(directory=static_dir), name="static")
background_indexer.start()
background_intelligence.start()
background_workspace_learning.start()
council.start()


def get_session_id(request: Request) -> str:
    session_id = request.headers.get("x-session-id", "").strip()
    if not session_id:
        raise HTTPException(status_code=400, detail="Missing X-Session-Id header")
    return session_id


@app.get("/")
def index() -> FileResponse:
    return FileResponse(static_dir / "index.html")


@app.get("/council")
def council_page() -> FileResponse:
    return FileResponse(static_dir / "council.html")


@app.get("/api/health")
def health() -> dict:
    return {
        "ok": True,
        "uploads_dir": str(UPLOADS_DIR),
        "generated_dir": str(GENERATED_DIR),
        "model_status": agent.get_model_status(),
        "memory": memory_stats(),
        "project_index": indexer.get_status(),
        "background_reindex": background_indexer.get_status(),
        "background_intelligence": background_intelligence.get_status(),
        "background_workspace_learning": background_workspace_learning.get_status(),
        "background_council": council.get_status(),
    }


@app.get("/api/files")
def list_files(request: Request) -> dict:
    session_id = get_session_id(request)
    uploads = sorted(str(p.relative_to(UPLOADS_DIR)) for p in UPLOADS_DIR.rglob("*") if p.is_file())
    generated = sorted(str(p.relative_to(GENERATED_DIR)) for p in GENERATED_DIR.rglob("*") if p.is_file())
    return {
        "uploads": uploads,
        "generated": generated,
        "lessons": load_recent_lessons(),
        "project_index": indexer.get_status(),
        "background_reindex": background_indexer.get_status(),
        "model_status": agent.get_model_status(),
        "memory": memory_stats(),
        "background_intelligence": background_intelligence.get_status(),
        "background_workspace_learning": background_workspace_learning.get_status(),
        "background_council": council.get_status(),
        "approval_request": agent.get_pending_approval(session_id),
        "session": agent.get_session_snapshot(session_id),
        "execution_boundary": {
            "mode": "guarded-local",
            "trusted_code_only": True,
            "summary": "Best-effort local restrictions for trusted tasks, not a hardened boundary for untrusted code.",
        },
    }


@app.get("/api/project-index")
def get_project_index() -> dict:
    return {
        "project_index": indexer.get_status(),
        "background_reindex": background_indexer.get_status(),
        "background_intelligence": background_intelligence.get_status(),
        "background_workspace_learning": background_workspace_learning.get_status(),
        "background_council": council.get_status(),
        "model_status": agent.get_model_status(),
        "memory": memory_stats(),
    }


@app.post("/api/project-index")
def rebuild_project_index() -> dict:
    result = indexer.index_project()
    return {
        **result,
        "background_reindex": background_indexer.get_status(),
        "background_intelligence": background_intelligence.get_status(),
        "background_workspace_learning": background_workspace_learning.get_status(),
        "background_council": council.get_status(),
    }


@app.post("/api/project-index/roots")
def update_project_roots(payload: ProjectRootsUpdateRequest) -> dict:
    try:
        result = indexer.configure_project_roots(payload.project_roots)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    background_indexer.reset_signature()
    status = indexer.get_status()
    status["roots"] = result["project_roots"]
    status["project_root_count"] = result["project_root_count"]
    return {
        **status,
        "background_reindex": background_indexer.get_status(),
        "background_intelligence": background_intelligence.get_status(),
        "background_workspace_learning": background_workspace_learning.get_status(),
        "background_council": council.get_status(),
    }


@app.get("/api/council")
def get_council_status(request: Request) -> dict:
    get_session_id(request)
    return {
        "background_council": council.get_status(),
        "model_status": agent.get_model_status(),
    }


@app.post("/api/council/settings")
def update_council_settings(payload: CouncilSettingsRequest, request: Request) -> dict:
    get_session_id(request)
    snapshot = council.update_settings(payload.model_dump())
    return {
        "background_council": snapshot,
    }


@app.post("/api/council/run")
def run_council_now(request: Request) -> dict:
    get_session_id(request)
    result = council.run_once(manual=True)
    return {
        "background_council": result,
    }


@app.get("/api/approval")
def get_approval(request: Request) -> dict:
    session_id = get_session_id(request)
    return {
        "approval_request": agent.get_pending_approval(session_id),
        "session": agent.get_session_snapshot(session_id),
    }


@app.post("/api/approval/approve", response_model=ChatResponse)
def approve_action(request: Request) -> ChatResponse:
    return ChatResponse(**agent.approve_pending(get_session_id(request)))


@app.post("/api/approval/reject", response_model=ChatResponse)
def reject_action(request: Request) -> ChatResponse:
    return ChatResponse(**agent.reject_pending(get_session_id(request)))


@app.post("/api/upload")
async def upload_file(file: UploadFile = File(...)) -> dict:
    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail="Uploaded file was empty.")
    target = save_uploaded_file(file.filename, content)
    return {"saved": str(target.name)}


@app.post("/api/chat", response_model=ChatResponse)
def chat(payload: ChatRequest, request: Request) -> ChatResponse:
    try:
        result = agent.chat(payload.message, payload.model, get_session_id(request))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return ChatResponse(**result)


@app.post("/api/generate", response_model=ChatResponse)
def generate(payload: GenerateRequest, request: Request) -> ChatResponse:
    try:
        result = agent.generate_file(payload.instruction, payload.filename, payload.model, get_session_id(request))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return ChatResponse(**result)


@app.post("/api/test", response_model=ChatResponse)
def run_micro_test(payload: TestRequest, request: Request) -> ChatResponse:
    try:
        result = agent.run_micro_test(payload.instruction, payload.model, get_session_id(request))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return ChatResponse(**result)


@app.post("/api/consensus")
def run_consensus(payload: ConsensusRequest, request: Request) -> dict:
    get_session_id(request)
    try:
        result = consensus_service.run(
            prompt=payload.prompt,
            model=payload.model,
            filename=payload.filename,
            top_k=payload.top_k,
            commit_to_git=payload.commit_to_git,
            push_to_remote=payload.push_to_remote,
            commit_message=payload.commit_message,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return {
        **result,
        "background_intelligence": background_intelligence.get_status(),
    }


@app.post("/api/feedback")
def feedback(payload: FeedbackRequest) -> dict:
    agent.record_feedback(payload.feedback, payload.successful)
    return {"saved": True}


@app.get("/generated/{file_path:path}")
def get_generated_file(file_path: str) -> FileResponse:
    target = (GENERATED_DIR / file_path).resolve()
    base = GENERATED_DIR.resolve()
    if not str(target).startswith(str(base)) or not target.exists():
        raise HTTPException(status_code=404, detail="File not found")
    return FileResponse(target)


@app.get("/uploads/{file_path:path}")
def get_uploaded_file(file_path: str) -> FileResponse:
    target = (UPLOADS_DIR / file_path).resolve()
    base = UPLOADS_DIR.resolve()
    if not str(target).startswith(str(base)) or not target.exists():
        raise HTTPException(status_code=404, detail="File not found")
    return FileResponse(target)
