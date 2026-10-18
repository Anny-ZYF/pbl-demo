from __future__ import annotations

import hashlib
from pathlib import Path

from scripts.train_model import _stratified_folds, _stratified_holdout, _write_json
from app.dataset import generate_paper_samples


def test_artifact_hash_survives_git_lf_normalization(tmp_path: Path) -> None:
    path = tmp_path / "metadata.json"
    _write_json(path, {"version": "test", "status": "ELIGIBLE"})
    generated = path.read_bytes()
    checked_out = generated.replace(b"\r\n", b"\n")
    assert hashlib.sha256(generated).digest() == hashlib.sha256(checked_out).digest()


def test_holdout_is_deterministic_and_stratified_by_actual_group_size() -> None:
    samples = generate_paper_samples()
    training, test, distribution = _stratified_holdout(samples)
    assert len(training) == 1429
    assert len(test) == 356
    assert set(training).isdisjoint(test)
    assert set(training) | set(test) == set(range(1785))
    assert distribution == {
        "1": {"total": 21, "training": 17, "test": 4},
        "2": {"total": 441, "training": 353, "test": 88},
        "3": {"total": 441, "training": 353, "test": 88},
        "4": {"total": 441, "training": 353, "test": 88},
        "5": {"total": 441, "training": 353, "test": 88},
    }


def test_five_folds_cover_training_rows_once_and_keep_every_stratum() -> None:
    samples = generate_paper_samples()
    training, _, _ = _stratified_holdout(samples)
    folds = _stratified_folds(training, samples)
    flattened = [index for fold in folds for index in fold]
    assert len(folds) == 5
    assert len(flattened) == len(training)
    assert len(set(flattened)) == len(training)
    for fold in folds:
        assert {samples[index].actual_group_size for index in fold} == {1, 2, 3, 4, 5}
