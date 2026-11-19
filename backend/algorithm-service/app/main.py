"""FastAPI entrypoint for smoothstep rule scoring with an optional XGBoost model."""

from __future__ import annotations

from fastapi import Depends, FastAPI

from app.model_runtime import ModelRuntime
from app.rules import MAX_SCORE, MIN_SCORE, RULE_VERSION, individual_score
from app.schemas import (
    BatchPredictionInput,
    BatchPredictionResponse,
    HealthResponse,
    ModelStatusResponse,
    StudentPrediction,
)
from app.security import bearer_scheme, require_internal_token


app = FastAPI(
    title="Groupwork Smoothstep Algorithm Service",
    version="2.0.0",
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)
model_runtime = ModelRuntime.load()


def _round_score(value: float) -> float:
    return round(value, 6)


def _authorise(credentials=Depends(bearer_scheme)) -> None:
    require_internal_token(credentials)


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="ok",
        ruleVersion=RULE_VERSION,
        modelAvailable=model_runtime.available,
        modelReason=model_runtime.reason,
    )


@app.get(
    "/v1/model",
    response_model=ModelStatusResponse,
    dependencies=[Depends(_authorise)],
)
def model_status() -> ModelStatusResponse:
    return ModelStatusResponse(
        available=model_runtime.available,
        reason=model_runtime.reason,
        metadata=model_runtime.metadata,
        metrics=model_runtime.metrics,
        hashes=model_runtime.hashes,
    )


@app.post(
    "/v1/predict",
    response_model=BatchPredictionResponse,
    dependencies=[Depends(_authorise)],
)
def predict(payload: BatchPredictionInput) -> BatchPredictionResponse:
    rule_scores = [
        individual_score(item.standardGroupSize, item.groupScore, item.contribution)
        for item in payload.items
    ]
    model_scores: dict[int, float] = {}
    prediction_failure: str | None = None

    # The model is trained over the whole p=1..4 grid, so every item is eligible.
    if model_runtime.available:
        try:
            predicted = model_runtime.predict_batch(
                [
                    [
                        item.standardGroupSize,
                        item.contribution,
                        item.groupScore,
                    ]
                    for item in payload.items
                ]
            )
            if len(predicted) != len(payload.items):
                raise RuntimeError("model returned an unexpected prediction count")
            model_scores = {
                index: min(MAX_SCORE, max(MIN_SCORE, score))
                for index, score in enumerate(predicted)
            }
        except Exception as error:  # Model failure must never block rule scoring.
            prediction_failure = f"model_prediction_failed:{type(error).__name__}"

    responses: list[StudentPrediction] = []
    for index, item in enumerate(payload.items):
        rule_score = rule_scores[index]
        model_score = model_scores.get(index)
        if not model_runtime.available:
            mode = "rule_fallback"
            explanation = model_runtime.reason
        elif prediction_failure is not None:
            mode = "rule_fallback"
            explanation = prediction_failure
        else:
            mode = "model"
            explanation = "xgboost_prediction"

        delta = abs(model_score - rule_score) if model_score is not None else None
        responses.append(
            StudentPrediction(
                studentId=item.studentId,
                ruleScore=_round_score(rule_score),
                modelScore=(
                    _round_score(model_score) if model_score is not None else None
                ),
                finalSuggestion=_round_score(
                    model_score if model_score is not None else rule_score
                ),
                mode=mode,
                delta=_round_score(delta) if delta is not None else None,
                anomaly=delta is not None and delta > 2.0,
                explanation=explanation,
            )
        )

    model_version = None
    artifact_sha256 = None
    if model_runtime.available and model_runtime.metadata:
        value = model_runtime.metadata.get("version")
        model_version = str(value) if value is not None else None
        hash_value = model_runtime.metadata.get("modelSha256")
        artifact_sha256 = str(hash_value) if hash_value is not None else None
    return BatchPredictionResponse(
        ruleVersion=RULE_VERSION,
        modelVersion=model_version,
        artifactSha256=artifact_sha256,
        items=responses,
    )
