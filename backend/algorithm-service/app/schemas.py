"""HTTP request and response contracts."""

from __future__ import annotations

from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field


UnitFloat = Annotated[float, Field(ge=0.0, le=1.0)]
ScoreFloat = Annotated[float, Field(ge=0.0, le=100.0)]


class ApiModel(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)


class StudentPredictionInput(ApiModel):
    studentId: str | int
    contribution: UnitFloat
    standardGroupSize: int = Field(ge=1, le=4)
    groupScore: ScoreFloat


class BatchPredictionInput(ApiModel):
    items: list[StudentPredictionInput] = Field(min_length=1, max_length=1000)


class StudentPrediction(ApiModel):
    studentId: str | int
    ruleScore: ScoreFloat
    modelScore: ScoreFloat | None
    finalSuggestion: ScoreFloat
    mode: Literal["model", "rule_fallback"]
    delta: float | None
    anomaly: bool
    explanation: str


class BatchPredictionResponse(ApiModel):
    ruleVersion: str
    modelVersion: str | None
    artifactSha256: str | None
    items: list[StudentPrediction]


class HealthResponse(ApiModel):
    status: Literal["ok"]
    ruleVersion: str
    modelAvailable: bool
    modelReason: str


class ModelStatusResponse(ApiModel):
    available: bool
    reason: str
    metadata: dict[str, Any] | None
    metrics: dict[str, Any] | None
    hashes: dict[str, Any] | None
