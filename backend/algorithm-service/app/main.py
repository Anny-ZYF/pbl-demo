"""FastAPI entrypoint for MCQS and optional XGBoost scoring."""

from __future__ import annotations

from fastapi import Depends, FastAPI

from app.model_runtime import ModelRuntime
from app.rules import RULE_VERSION, individual_score
from app.schemas import (
    BatchPredictionInput,
    BatchPredictionResponse,
    HealthResponse,
    ModelStatusResponse,
    StudentPrediction,
)
from app.security import bearer_scheme, require_internal_token


app = FastAPI(
    title="Groupwork MCQS Algorithm Service",
    version="1.0.0",
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
        individual_score(
            item.standardGroupSize,
            item.actualGroupSize,
            item.groupScore,
            item.contribution,
        )
        for item in payload.items
    ]
    eligible_indices = [
        index
        for index, item in enumerate(payload.items)
        if item.standardGroupSize == 4
    ]
    model_scores: dict[int, float] = {}
    prediction_failure: str | None = None

    if eligible_indices and model_runtime.available:
        try:
            predicted = model_runtime.predict_batch(
                [
                    [
                        payload.items[index].contribution,
                        payload.items[index].actualGroupSize,
                        payload.items[index].groupScore,
                    ]
                    for index in eligible_indices
                ]
            )
            if len(predicted) != len(eligible_indices):
                raise RuntimeError("model returned an unexpected prediction count")
            model_scores = {
                item_index: min(100.0, max(0.0, score))
                for item_index, score in zip(eligible_indices, predicted, strict=True)
            }
        except Exception as error:  # Model failure must never block rule scoring.
            prediction_failure = f"model_prediction_failed:{type(error).__name__}"

    responses: list[StudentPrediction] = []
    for index, item in enumerate(payload.items):
        rule_score = rule_scores[index]
        model_score = model_scores.get(index)
        if item.standardGroupSize != 4:
            mode = "rule_only"
            explanation = "standard_group_size_not_supported_by_model"
        elif not model_runtime.available:
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
