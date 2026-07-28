from __future__ import annotations

from scripts.train_model import _stratified_folds, _stratified_holdout
from app.dataset import generate_paper_samples


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
