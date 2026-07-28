"""Optional, checksum-verified XGBoost runtime."""

from __future__ import annotations

import hashlib
import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Sequence


FEATURE_ORDER = ["contribution", "actual_group_size", "group_score"]
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
    _booster: Any = field(default=None, repr=False)
    _xgboost: Any = field(default=None, repr=False)

    @classmethod
    def load(cls, model_dir: Path | None = None) -> "ModelRuntime":
        root = model_dir or Path(
            os.getenv("ALGORITHM_MODEL_DIR", str(DEFAULT_MODEL_DIR))
        )
        model_path = root / "xgboost-model.json"
        metadata_path = root / "metadata.json"
        metrics_path = root / "metrics.json"
        hashes_path = root / "sha256.json"

        required = [model_path, metadata_path, metrics_path, hashes_path]
        if not all(path.is_file() for path in required):
            return cls(False, "model_artifacts_missing")

        try:
            metadata = _read_json(metadata_path)
            metrics = _read_json(metrics_path)
            hashes = _read_json(hashes_path)
            expected_hashes = hashes.get("files", {})
            if not isinstance(expected_hashes, dict):
                raise ValueError("sha256.json files must be an object")
            for path in (model_path, metadata_path, metrics_path):
                if expected_hashes.get(path.name) != _sha256(path):
                    raise ValueError(f"checksum mismatch for {path.name}")
            if metadata.get("modelSha256") != expected_hashes.get(model_path.name):
                raise ValueError("metadata modelSha256 does not match the model manifest")
            if metadata.get("featureOrder") != FEATURE_ORDER:
                raise ValueError("unsupported model feature order")
            if metadata.get("status") != "ELIGIBLE":
                return cls(
                    False,
                    "model_not_eligible",
                    metadata=metadata,
                    metrics=metrics,
                    hashes=hashes,
                )

            try:
                import xgboost as xgb
            except ImportError:
                return cls(
                    False,
                    "xgboost_dependency_missing",
                    metadata=metadata,
                    metrics=metrics,
                    hashes=hashes,
                )

            booster = xgb.Booster()
            booster.load_model(model_path)
            return cls(
                True,
                "loaded",
                metadata=metadata,
                metrics=metrics,
                hashes=hashes,
                _booster=booster,
                _xgboost=xgb,
            )
        except (OSError, ValueError, json.JSONDecodeError) as error:
            return cls(False, f"model_load_failed:{error}")

    def predict_batch(self, features: Sequence[Sequence[float]]) -> list[float]:
        if not self.available or self._booster is None or self._xgboost is None:
            raise RuntimeError(self.reason)
        matrix = self._xgboost.DMatrix(features, feature_names=FEATURE_ORDER)
        values = self._booster.predict(matrix)
        return [float(value) for value in values]
