from typing import Any

from pydantic import BaseModel, Field


class ChatRequest(BaseModel):
    message: str = Field(min_length=1)
    model: str = Field(default="qwen2.5:7b-instruct-q4_K_M")


class ChatResponse(BaseModel):
    reply: str
    created_files: list[str] = Field(default_factory=list)
    lessons_used: list[str] = Field(default_factory=list)
    tool_events: list[str] = Field(default_factory=list)
    updates: list[str] = Field(default_factory=list)
    figured_out: list[str] = Field(default_factory=list)
    ideas: list[str] = Field(default_factory=list)
    trust_report: dict[str, Any] = Field(default_factory=dict)
    requires_approval: bool = False
    approval_request: dict[str, Any] | None = None
    session: dict[str, Any] = Field(default_factory=dict)
    memory: dict[str, Any] = Field(default_factory=dict)


class GenerateRequest(BaseModel):
    instruction: str = Field(min_length=1)
    filename: str = Field(min_length=1)
    model: str = Field(default="qwen2.5:7b-instruct-q4_K_M")


class TestRequest(BaseModel):
    instruction: str = Field(min_length=1)
    model: str = Field(default="qwen2.5:7b-instruct-q4_K_M")


class FeedbackRequest(BaseModel):
    feedback: str = Field(min_length=1)
    successful: bool = True


class ProjectRootsUpdateRequest(BaseModel):
    project_roots: list[str] = Field(min_length=1)
