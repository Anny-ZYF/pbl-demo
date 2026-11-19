from __future__ import annotations

import builtins
import hashlib
import json
import shutil
from pathlib import Path

from app.model_runtime import DATASET_FILENAME, FEATURE_ORDER, MODEL_FILENAME, ModelRuntime


ARTIFACT_FILES = (
    MODEL_FILENAME,
    DATASET_FILENAME,
    "metadata.json",
    "metrics.json",
    "sha256.json",
)


def _copy_artifacts(target: Path) -> Path:
    source = Path(__file__).resolve().parents[1] / "artifacts"
    for name in ARTIFACT_FILES:
        shutil.copy2(source / name, target / name)
    return target


def _rewrite_json(path: Path, value: dict) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    hashes_path = path.parent / "sha256.json"
    hashes = json.loads(hashes_path.read_text(encoding="utf-8"))
    hashes["files"][path.name] = hashlib.sha256(path.read_bytes()).hexdigest()
    _rewrite_json_raw(hashes_path, hashes)


def _rewrite_json_raw(path: Path, value: dict) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def test_delivered_artifacts_load_and_predict_within_range() -> None:
    runtime = ModelRuntime.load()
    assert runtime.available is True
    assert runtime.reason == "loaded"
    assert runtime.metadata["modelFormat"] == "joblib-sklearn"
    assert runtime.metadata["featureOrder"] == FEATURE_ORDER
    assert runtime.metadata["ruleVersion"] == "smoothstep-v1"
    predictions = runtime.predict_batch([[2, 0.6, 60], [4, 0.25, 80], [1, 1.0, 100]])
    assert len(predictions) == 3
    assert all(0.0 <= value <= 100.0 for value in predictions)


def test_model_predictions_are_clipped_to_the_declared_score_range() -> None:
    runtime = ModelRuntime.load()
    assert runtime.available is True
    # The delivered booster predicts about 100.1041 for this extreme row;
    # without clipping it would violate the 0..100 contract and make Java
    # reject the whole batch.
    assert runtime.predict_batch([[4, 1.0, 100]]) == [100.0]
    assert all(0.0 <= v <= 100.0 for v in runtime.predict_batch(
        [[p, x, g] for p in (1, 2, 3, 4) for g in (0, 100) for x in (0.0, 0.5, 1.0)]
    ))


def test_model_artifacts_do_not_make_sklearn_a_startup_requirement(monkeypatch) -> None:
    original_import = builtins.__import__

    def import_without_sklearn(name, *args, **kwargs):
        if name == "sklearn":
            raise ImportError("sklearn intentionally unavailable")
        return original_import(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", import_without_sklearn)
    artifact_dir = Path(__file__).resolve().parents[1] / "artifacts"
    runtime = ModelRuntime.load(artifact_dir)
    assert runtime.available is False
    assert runtime.reason == "model_dependency_missing"


def test_not_eligible_artifact_is_never_loaded(tmp_path: Path) -> None:
    _copy_artifacts(tmp_path)
    metadata_path = tmp_path / "metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    metadata["status"] = "NOT_ELIGIBLE"
    _rewrite_json(metadata_path, metadata)

    runtime = ModelRuntime.load(tmp_path)
    assert runtime.available is False
    assert runtime.reason == "model_not_eligible"


def test_metadata_model_hash_must_match_the_manifest(tmp_path: Path) -> None:
    _copy_artifacts(tmp_path)
    metadata_path = tmp_path / "metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    metadata["modelSha256"] = "0" * 64
    _rewrite_json(metadata_path, metadata)

    runtime = ModelRuntime.load(tmp_path)
    assert runtime.available is False
    assert "metadata modelSha256" in runtime.reason


def test_dataset_hash_must_match_the_manifest(tmp_path: Path) -> None:
    _copy_artifacts(tmp_path)
    metadata_path = tmp_path / "metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    metadata["datasetSha256"] = "0" * 64
    _rewrite_json(metadata_path, metadata)

    runtime = ModelRuntime.load(tmp_path)
    assert runtime.available is False
    assert "datasetSha256" in runtime.reason


def test_rule_version_mismatch_disables_the_model(tmp_path: Path) -> None:
    _copy_artifacts(tmp_path)
    metadata_path = tmp_path / "metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    metadata["ruleVersion"] = "paper-mcqs-v1"
    _rewrite_json(metadata_path, metadata)

    runtime = ModelRuntime.load(tmp_path)
    assert runtime.available is False
    assert "rule version mismatch" in runtime.reason
