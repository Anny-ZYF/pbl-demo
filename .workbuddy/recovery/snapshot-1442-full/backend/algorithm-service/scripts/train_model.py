"""Reproduce the 1108.py training run as an optional retraining utility.

The checked-in ``artifacts/score-model.joblib`` is the authoritative model
delivered by the project owner. This script writes a *candidate* model to a
separate path and never overwrites that artifact. After choosing a model, run
``scripts/build_artifacts.py`` to regenerate metadata, metrics and the checksum
manifest from measured holdout results.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.model_selection import train_test_split
from xgboost import XGBRegressor

from app.dataset import FEATURE_ORDER, TARGET_COLUMN, build_grid_frame
from app.rules import BOOST_CAP, RULE_VERSION


SEED = 42
TEST_SIZE = 0.20
PARAMETERS: dict[str, Any] = {
    "n_estimators": 150,
    "max_depth": 5,
    "learning_rate": 0.07,
    "random_state": SEED,
}


def _write_json(path: Path, value: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _metrics(actual: np.ndarray, predicted: np.ndarray) -> dict[str, float]:
    errors = predicted - actual
    rmse = math.sqrt(float(np.mean(errors * errors)))
    mae = float(np.mean(np.abs(errors)))
    denominator = float(np.sum((actual - float(np.mean(actual))) ** 2))
    r2 = 1.0 - float(np.sum(errors * errors)) / denominator
    return {"rmse": rmse, "mae": mae, "r2": r2}


def train(output_path: Path) -> dict[str, Any]:
    frame = build_grid_frame()
    X = frame[FEATURE_ORDER]
    y = frame[TARGET_COLUMN]
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=TEST_SIZE, random_state=SEED
    )

    model = XGBRegressor(**PARAMETERS)
    model.fit(X_train, y_train)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, output_path)

    predicted = np.asarray(model.predict(X_test), dtype=float)
    return {
        "output": str(output_path),
        "ruleVersion": RULE_VERSION,
        "boostCap": BOOST_CAP,
        "parameters": PARAMETERS,
        "datasetRows": int(len(frame)),
        "trainingRows": int(len(X_train)),
        "testRows": int(len(X_test)),
        "holdout": _metrics(y_test.to_numpy(dtype=float), predicted),
        "featureOrder": FEATURE_ORDER,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("artifacts/candidate-model.joblib"),
        help="candidate model path; the deployed artifact is never overwritten",
    )
    args = parser.parse_args()
    print(json.dumps(train(args.output), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
