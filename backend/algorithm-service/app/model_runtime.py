"""Optional, checksum-verified sklearn XGBoost (joblib) runtime."""

from __future__ import annotations

import hashlib
import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Sequence

from app.rules import MAX_SCORE, MIN_SCORE, RULE_VERSION


FEATURE_ORDER = ["标准人数", "个人贡献度", "作业总分"]
MODEL_FILENAME = "score-model.joblib"
DATASET_FILENAME = "score-samples.csv"
DEFAULT_MODEL_DIR = Path(__file__).resolve().parents[1] / "artifacts"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _read_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as source:
        value = json.load(source)
    if not isinstance(value, dict):
        raise ValueError(f"{path.name} must contain a JSON object")
    return value


@dataclass
class ModelRuntime:
    available: bool
    reason: str
    metadata: dict[str, Any] | None = None
    metrics: dict[str, Any] | None = None
    hashes: dict[str, Any] | None = None
    _model: Any = field(default=None, repr=False)
    _pandas: Any = field(default=None, repr=False)

    @classmethod
    def load(cls, model_dir: Path | None = None) -> "ModelRuntime":
        root = model_dir or Path(
            os.getenv("ALGORITHM_MODEL_DIR", str(DEFAULT_MODEL_DIR))
        )
        model_path = root / MODEL_FILENAME
        dataset_path = root / DATASET_FILENAME
        metadata_path = root / "metadata.json"
        metrics_path = root / "metrics.json"
        hashes_path = root / "sha256.json"

        required = [
            model_path,
            dataset_path,
            metadata_path,
            metrics_path,
            hashes_path,
        ]
        if not all(path.is_file() for path in required):
            return cls(False, "model_artifacts_missing")

        try:
            metadata = _read_json(metadata_path)
            metrics = _read_json(metrics_path)
            hashes = _read_json(hashes_path)
            expected_hashes = hashes.get("files", {})
            if not isinstance(expected_hashes, dict):
                raise ValueError("sha256.json files must be an object")
            for path in (model_path, dataset_path, metadata_path, metrics_path):
                if expected_hashes.get(path.name) != _sha256(path):
                    raise ValueError(f"checksum mismatch for {path.name}")
            if metadata.get("modelSha256") != expected_hashes.get(model_path.name):
                raise ValueError("metadata modelSha256 does not match the model manifest")
            if metadata.get("datasetSha256") != expected_hashes.get(dataset_path.name):
                raise ValueError("metadata datasetSha256 does not match the manifest")
            if metadata.get("modelFormat") != "joblib-sklearn":
                raise ValueError("unsupported model format")
            if metadata.get("featureOrder") != FEATURE_ORDER:
                raise ValueError("unsupported model feature order")
            if metadata.get("ruleVersion") != RULE_VERSION:
                raise ValueError("rule version mismatch between model and rule engine")
            if metadata.get("status") != "ELIGIBLE" or metrics.get("eligible") is not True:
                return cls(
                    False,
                    "model_not_eligible",
                    metadata=metadata,
                    metrics=metrics,
                    hashes=hashes,
                )

            try:
                import joblib
                import pandas as pd
                import sklearn  # noqa: F401  (required to unpickle XGBRegressor)
                import xgboost  # noqa: F401
            except ImportError:
                return cls(
                    False,
                    "model_dependency_missing",
                    metadata=metadata,
                    metrics=metrics,
                    hashes=hashes,
                )

            model = joblib.load(model_path)
            names_attr = getattr(model, "feature_names_in_", None)
            names = [str(name) for name in names_attr] if names_attr is not None else []
            if names and names != FEATURE_ORDER:
                raise ValueError(f"model feature names mismatch: {names}")
            return cls(
                True,
                "loaded",
                metadata=metadata,
                metrics=metrics,
                hashes=hashes,
                _model=model,
                _pandas=pd,
            )
        except (OSError, ValueError, json.JSONDecodeError) as error:
            return cls(False, f"model_load_failed:{error}")

    def predict_batch(self, features: Sequence[Sequence[float]]) -> list[float]:
        """features rows must be ordered [standardGroupSize, contribution, groupScore]."""

        if not self.available or self._model is None or self._pandas is None:
            raise RuntimeError(self.reason)
        frame = self._pandas.DataFrame(list(features), columns=FEATURE_ORDER)
        values = self._model.predict(frame)
        # XGBoost regression can extrapolate slightly outside the label range.
        # The score cap is enforced here as well so every caller sees 0..100.
        return [min(MAX_SCORE, max(MIN_SCORE, float(value))) for value in values]
