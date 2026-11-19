"""Deterministic reconstruction of the scoring grid used by 1108.py."""

from __future__ import annotations

import hashlib
import io
from pathlib import Path

import numpy as np
import pandas as pd

from app.rules import BOOST_CAP, MAX_SCORE, MIN_SCORE


FEATURE_ORDER = ["标准人数", "个人贡献度", "作业总分"]
TARGET_COLUMN = "个人成绩"
DATASET_COLUMNS = (*FEATURE_ORDER, TARGET_COLUMN)


def calc_score_vector(
    p: np.ndarray, x: np.ndarray, g: np.ndarray, b: float = BOOST_CAP
) -> np.ndarray:
    """Vectorized form of calc_score() in 1108.py, in the same arithmetic order."""

    threshold = 1.0 / p
    t = np.zeros_like(x)
    score = np.zeros_like(x)

    low = x <= threshold
    t[low] = x[low] * p[low]
    score[low] = g[low] * (3 * t[low] ** 2 - 2 * t[low] ** 3)

    high = x > threshold
    denom = 1 - 1.0 / p[high]
    t[high] = (x[high] - 1.0 / p[high]) / denom
    score[high] = g[high] + b * (3 * t[high] ** 2 - 2 * t[high] ** 3)

    return np.clip(score, MIN_SCORE, MAX_SCORE)


def build_grid_frame() -> pd.DataFrame:
    """Reproduce the 1108.py meshgrid: p=1..4, x=0..1 step .01, G=0..100 step 1."""

    p_list = np.arange(1, 5, 1)
    x_list = np.arange(0, 1.01, 0.01)
    g_list = np.arange(0, 101, 1)
    p, x, g = np.meshgrid(p_list, x_list, g_list, indexing="ij")
    data = np.column_stack([p.ravel(), x.ravel(), g.ravel()])
    frame = pd.DataFrame(data, columns=FEATURE_ORDER)
    frame[TARGET_COLUMN] = calc_score_vector(data[:, 0], data[:, 1], data[:, 2])
    return frame


def dataset_csv_bytes(frame: pd.DataFrame) -> bytes:
    buffer = io.StringIO(newline="")
    frame.to_csv(buffer, index=False, float_format="%.12f", lineterminator="\n")
    return buffer.getvalue().encode("utf-8")


def dataset_sha256(frame: pd.DataFrame) -> str:
    return hashlib.sha256(dataset_csv_bytes(frame)).hexdigest()


def write_dataset(path: Path, frame: pd.DataFrame | None = None) -> str:
    rows = frame if frame is not None else build_grid_frame()
    content = dataset_csv_bytes(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return hashlib.sha256(content).hexdigest()
