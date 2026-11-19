from __future__ import annotations

import hashlib
from pathlib import Path

import numpy as np
import pytest

from app.dataset import build_grid_frame
from scripts.train_model import PARAMETERS, SEED, TEST_SIZE, _metrics, _write_json


def test_artifact_hash_survives_git_lf_normalization(tmp_path: Path) -> None:
    path = tmp_path / "metadata.json"
    _write_json(path, {"version": "test", "status": "ELIGIBLE"})
    generated = path.read_bytes()
    checked_out = generated.replace(b"\r\n", b"\n")
    assert hashlib.sha256(generated).digest() == hashlib.sha256(checked_out).digest()


def test_parameters_match_the_generation_script() -> None:
    assert PARAMETERS == {
        "n_estimators": 150,
        "max_depth": 5,
        "learning_rate": 0.07,
        "random_state": 42,
    }
    assert SEED == 42
    assert TEST_SIZE == 0.20


def test_metrics_are_computed_exactly() -> None:
    actual = np.array([0.0, 50.0, 100.0])
    perfect = _metrics(actual, np.array([0.0, 50.0, 100.0]))
    assert perfect == {"rmse": 0.0, "mae": 0.0, "r2": 1.0}

    biased = _metrics(actual, np.array([2.0, 52.0, 102.0]))
    assert biased["rmse"] == pytest.approx(2.0)
    assert biased["mae"] == pytest.approx(2.0)
    assert biased["r2"] < 1.0


def test_grid_is_deterministic_for_training() -> None:
    first = build_grid_frame()
    second = build_grid_frame()
    assert len(first) == 40804
    assert first.equals(second)
