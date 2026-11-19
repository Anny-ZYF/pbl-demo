from __future__ import annotations

import numpy as np

from app.dataset import (
    DATASET_COLUMNS,
    FEATURE_ORDER,
    TARGET_COLUMN,
    build_grid_frame,
    calc_score_vector,
    dataset_sha256,
    write_dataset,
)
from app.rules import MAX_SCORE, MIN_SCORE, individual_score


def test_grid_matches_the_generation_script() -> None:
    frame = build_grid_frame()
    assert len(frame) == 40804  # 4 p-values x 101 contributions x 101 group scores
    assert list(frame.columns) == list(DATASET_COLUMNS)
    assert frame[FEATURE_ORDER[0]].iloc[0] == 1
    assert frame[FEATURE_ORDER[1]].iloc[0] == 0.0
    assert frame[FEATURE_ORDER[2]].iloc[0] == 0.0


def test_grid_is_deterministic() -> None:
    first = build_grid_frame()
    second = build_grid_frame()
    assert first.equals(second)
    assert dataset_sha256(first) == dataset_sha256(second)


def test_grid_labels_follow_the_deterministic_rule() -> None:
    frame = build_grid_frame()
    sample = frame.iloc[12345]
    expected = individual_score(
        int(sample[FEATURE_ORDER[0]]),
        float(sample[FEATURE_ORDER[2]]),
        float(sample[FEATURE_ORDER[1]]),
    )
    assert sample[TARGET_COLUMN] == np.float64(expected)
    scores = frame[TARGET_COLUMN].to_numpy()
    assert scores.min() >= MIN_SCORE
    assert scores.max() <= MAX_SCORE


def test_vectorized_formula_agrees_with_scalar_rule() -> None:
    p = np.array([1.0, 2.0, 3.0, 4.0])
    x = np.array([1.0, 0.6, 0.4, 0.1])
    g = np.array([100.0, 60.0, 75.0, 80.0])
    vectorized = calc_score_vector(p, x, g)
    expected = [
        individual_score(int(pi), float(gi), float(xi))
        for pi, xi, gi in zip(p, x, g, strict=True)
    ]
    np.testing.assert_allclose(vectorized, expected, atol=1e-12)


def test_write_dataset_writes_the_same_bytes_as_the_hash(tmp_path) -> None:
    frame = build_grid_frame()
    path = tmp_path / "samples.csv"
    digest = write_dataset(path, frame)
    assert digest == dataset_sha256(frame)
    assert path.read_bytes().decode("utf-8").splitlines()[0].startswith("标准人数")
