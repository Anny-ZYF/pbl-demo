from __future__ import annotations

import builtins
import hashlib
import json
import shutil
from pathlib import Path

from app.model_runtime import ModelRuntime


def test_model_artifacts_do_not_make_xgboost_a_startup_requirement(monkeypatch) -> None:
    original_import = builtins.__import__

    def import_without_xgboost(name, *args, **kwargs):
        if name == "xgboost":
            raise ImportError("xgboost intentionally unavailable")
        return original_import(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", import_without_xgboost)
    artifact_dir = Path(__file__).resolve().parents[1] / "artifacts"
    runtime = ModelRuntime.load(artifact_dir)
    assert runtime.available is False
    assert runtime.reason == "xgboost_dependency_missing"


def test_not_eligible_artifact_is_never_loaded(tmp_path: Path) -> None:
    source = Path(__file__).resolve().parents[1] / "artifacts"
    for name in ("xgboost-model.json", "metadata.json", "metrics.json", "sha256.json"):
        shutil.copy2(source / name, tmp_path / name)

    metadata_path = tmp_path / "metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    metadata["status"] = "NOT_ELIGIBLE"
    metadata_path.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    hashes_path = tmp_path / "sha256.json"
    hashes = json.loads(hashes_path.read_text(encoding="utf-8"))
    hashes["files"]["metadata.json"] = hashlib.sha256(
        metadata_path.read_bytes()
    ).hexdigest()
    hashes_path.write_text(
        json.dumps(hashes, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    runtime = ModelRuntime.load(tmp_path)
    assert runtime.available is False
    assert runtime.reason == "model_not_eligible"


def test_metadata_model_hash_must_match_the_manifest(tmp_path: Path) -> None:
    source = Path(__file__).resolve().parents[1] / "artifacts"
    for name in ("xgboost-model.json", "metadata.json", "metrics.json", "sha256.json"):
        shutil.copy2(source / name, tmp_path / name)

    metadata_path = tmp_path / "metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    metadata["modelSha256"] = "0" * 64
    metadata_path.write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    hashes_path = tmp_path / "sha256.json"
    hashes = json.loads(hashes_path.read_text(encoding="utf-8"))
    hashes["files"]["metadata.json"] = hashlib.sha256(
        metadata_path.read_bytes()
    ).hexdigest()
    hashes_path.write_text(
        json.dumps(hashes, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )

    runtime = ModelRuntime.load(tmp_path)
    assert runtime.available is False
    assert "metadata modelSha256" in runtime.reason
