"""Train, validate, and save an XGBoost model without pickle artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from statistics import fmean, pstdev
from typing import Any, Sequence

from app.dataset import SyntheticSample, dataset_sha256, generate_paper_samples, write_dataset
from app.model_runtime import FEATURE_ORDER
from app.rules import RULE_VERSION


SEED = 2025
HOLDOUT_FRACTION = 0.20
CV_FOLDS = 5
ELIGIBILITY_THRESHOLDS = {"rmseMax": 1.0, "maeMax": 0.8, "r2Min": 0.99}

# Deterministically tuned defaults. Use --search to run BayesSearchCV instead.
FIXED_PARAMETERS: dict[str, Any] = {
    "n_estimators": 800,
    "learning_rate": 0.04,
    "max_depth": 6,
    "min_child_weight": 0.5,
    "subsample": 1.0,
    "colsample_bytree": 1.0,
    "reg_alpha": 0.0,
    "reg_lambda": 1.0,
    "objective": "reg:squarederror",
    "eval_metric": "rmse",
    "random_state": SEED,
    "n_jobs": 1,
    "tree_method": "hist",
}


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _write_json(path: Path, value: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def _metrics(actual: Sequence[float], predicted: Sequence[float]) -> dict[str, float]:
    actual_values = [float(value) for value in actual]
    predicted_values = [float(value) for value in predicted]
    if len(actual_values) != len(predicted_values) or not actual_values:
        raise ValueError("metric inputs must have the same non-zero length")
    errors = [predicted - actual for actual, predicted in zip(actual_values, predicted_values, strict=True)]
    rmse = math.sqrt(math.fsum(error * error for error in errors) / len(errors))
    mae = math.fsum(abs(error) for error in errors) / len(errors)
    mean_actual = math.fsum(actual_values) / len(actual_values)
    denominator = math.fsum((value - mean_actual) ** 2 for value in actual_values)
    r_squared = 1.0 - math.fsum(error * error for error in errors) / denominator
    return {"rmse": rmse, "mae": mae, "r2": r_squared}


def _stratified_holdout(
    samples: Sequence[SyntheticSample],
) -> tuple[list[int], list[int], dict[str, dict[str, int]]]:
    buckets: dict[int, list[int]] = defaultdict(list)
    for index, sample in enumerate(samples):
        buckets[sample.actual_group_size].append(index)

    randomizer = random.Random(SEED)
    training: list[int] = []
    test: list[int] = []
    distribution: dict[str, dict[str, int]] = {}
    for group_size in sorted(buckets):
        indices = buckets[group_size][:]
        randomizer.shuffle(indices)
        test_count = round(len(indices) * HOLDOUT_FRACTION)
        test.extend(indices[:test_count])
        training.extend(indices[test_count:])
        distribution[str(group_size)] = {
            "total": len(indices),
            "training": len(indices) - test_count,
            "test": test_count,
        }
    randomizer.shuffle(training)
    randomizer.shuffle(test)
    return training, test, distribution


def _stratified_folds(
    training_indices: Sequence[int],
    samples: Sequence[SyntheticSample],
) -> list[list[int]]:
    buckets: dict[int, list[int]] = defaultdict(list)
    for index in training_indices:
        buckets[samples[index].actual_group_size].append(index)

    randomizer = random.Random(SEED)
    folds: list[list[int]] = [[] for _ in range(CV_FOLDS)]
    for group_size in sorted(buckets):
        indices = buckets[group_size][:]
        randomizer.shuffle(indices)
        for position, index in enumerate(indices):
            folds[position % CV_FOLDS].append(index)
    for fold in folds:
        randomizer.shuffle(fold)
    return folds


def _booster_parameters(parameters: dict[str, Any]) -> dict[str, Any]:
    return {
        "objective": parameters["objective"],
        "eval_metric": parameters["eval_metric"],
        "eta": parameters["learning_rate"],
        "max_depth": parameters["max_depth"],
        "min_child_weight": parameters["min_child_weight"],
        "subsample": parameters["subsample"],
        "colsample_bytree": parameters["colsample_bytree"],
        "reg_alpha": parameters["reg_alpha"],
        "reg_lambda": parameters["reg_lambda"],
        "seed": parameters["random_state"],
        "nthread": parameters["n_jobs"],
        "tree_method": parameters["tree_method"],
    }


def _train_booster(xgb: Any, features: Any, targets: Any, indices: Sequence[int], parameters: dict[str, Any]) -> Any:
    matrix = xgb.DMatrix(
        features[list(indices)],
        label=targets[list(indices)],
        feature_names=FEATURE_ORDER,
    )
    return xgb.train(
        _booster_parameters(parameters),
        matrix,
        num_boost_round=int(parameters["n_estimators"]),
    )


def _predict_booster(xgb: Any, booster: Any, features: Any, indices: Sequence[int]) -> list[float]:
    matrix = xgb.DMatrix(features[list(indices)], feature_names=FEATURE_ORDER)
    return [float(value) for value in booster.predict(matrix)]


def _cross_validate(
    xgb: Any,
    features: Any,
    targets: Any,
    folds: Sequence[Sequence[int]],
    parameters: dict[str, Any],
) -> dict[str, Any]:
    fold_results: list[dict[str, Any]] = []
    for fold_index, validation_indices in enumerate(folds):
        training_indices = [
            index
            for other_index, fold in enumerate(folds)
            if other_index != fold_index
            for index in fold
        ]
        booster = _train_booster(
            xgb, features, targets, training_indices, parameters
        )
        predicted = _predict_booster(xgb, booster, features, validation_indices)
        result = _metrics(targets[list(validation_indices)], predicted)
        fold_results.append(
            {
                "fold": fold_index + 1,
                "trainingRows": len(training_indices),
                "validationRows": len(validation_indices),
                **result,
            }
        )

    summary: dict[str, dict[str, float]] = {}
    for metric in ("rmse", "mae", "r2"):
        values = [float(result[metric]) for result in fold_results]
        summary[metric] = {"mean": fmean(values), "std": pstdev(values)}
    return {"folds": fold_results, "summary": summary}


def _normalise_search_value(value: Any) -> Any:
    return value.item() if hasattr(value, "item") else value


def _bayes_search(
    xgb: Any,
    features: Any,
    targets: Any,
    training_indices: Sequence[int],
    samples: Sequence[SyntheticSample],
    iterations: int,
) -> tuple[dict[str, Any], dict[str, Any]]:
    try:
        from sklearn.model_selection import StratifiedKFold
        from skopt import BayesSearchCV
        from skopt.space import Integer, Real
    except ImportError as error:
        raise SystemExit(
            "Bayesian search dependencies are missing; install requirements-train.txt"
        ) from error

    x_train = features[list(training_indices)]
    y_train = targets[list(training_indices)]
    strata = [samples[index].actual_group_size for index in training_indices]
    splitter = StratifiedKFold(n_splits=CV_FOLDS, shuffle=True, random_state=SEED)
    cv_indices = list(splitter.split(x_train, strata))
    estimator = xgb.XGBRegressor(
        objective="reg:squarederror",
        eval_metric="rmse",
        random_state=SEED,
        n_jobs=1,
        tree_method="hist",
    )
    search = BayesSearchCV(
        estimator=estimator,
        search_spaces={
            "n_estimators": Integer(500, 1600),
            "learning_rate": Real(0.015, 0.08, prior="log-uniform"),
            "max_depth": Integer(4, 9),
            "min_child_weight": Real(0.2, 3.0, prior="log-uniform"),
            "subsample": Real(0.8, 1.0),
            "colsample_bytree": Real(0.8, 1.0),
            "reg_alpha": Real(1e-8, 0.3, prior="log-uniform"),
            "reg_lambda": Real(0.5, 3.0, prior="log-uniform"),
        },
        n_iter=iterations,
        scoring="neg_root_mean_squared_error",
        cv=cv_indices,
        random_state=SEED,
        n_jobs=1,
        refit=False,
        return_train_score=False,
    )
    search.fit(x_train, y_train)
    best_parameters = dict(FIXED_PARAMETERS)
    best_parameters.update(
        {
            key: _normalise_search_value(value)
            for key, value in search.best_params_.items()
        }
    )
    metadata = {
        "method": "BayesSearchCV",
        "requested": True,
        "executed": True,
        "iterations": iterations,
        "scoring": "neg_root_mean_squared_error",
        "bestCvRmse": -float(search.best_score_),
        "bestParameters": {
            key: _normalise_search_value(value)
            for key, value in search.best_params_.items()
        },
    }
    return best_parameters, metadata


def _is_eligible(metrics: dict[str, float]) -> bool:
    return (
        metrics["rmse"] <= ELIGIBILITY_THRESHOLDS["rmseMax"]
        and metrics["mae"] <= ELIGIBILITY_THRESHOLDS["maeMax"]
        and metrics["r2"] >= ELIGIBILITY_THRESHOLDS["r2Min"]
    )


def train(output_dir: Path, run_search: bool = False, search_iterations: int = 30) -> None:
    try:
        import numpy as np
        import xgboost as xgb
    except ImportError as error:
        raise SystemExit(
            "training dependencies are missing; install requirements-train.txt"
        ) from error

    samples = generate_paper_samples()
    features = np.asarray(
        [
            [sample.contribution, sample.actual_group_size, sample.group_score]
            for sample in samples
        ],
        dtype=float,
    )
    targets = np.asarray(
        [sample.individual_score for sample in samples], dtype=float
    )
    training_indices, test_indices, split_distribution = _stratified_holdout(samples)
    folds = _stratified_folds(training_indices, samples)

    if run_search:
        parameters, search_metadata = _bayes_search(
            xgb,
            features,
            targets,
            training_indices,
            samples,
            search_iterations,
        )
    else:
        parameters = dict(FIXED_PARAMETERS)
        search_metadata = {
            "method": "BayesSearchCV",
            "requested": False,
            "executed": False,
            "note": "Artifact uses checked-in deterministic tuned parameters; run with --search to execute Bayesian search.",
        }

    cross_validation = _cross_validate(
        xgb, features, targets, folds, parameters
    )
    booster = _train_booster(
        xgb, features, targets, training_indices, parameters
    )
    test_predictions = _predict_booster(
        xgb, booster, features, test_indices
    )
    holdout_metrics = _metrics(targets[list(test_indices)], test_predictions)
    eligible = _is_eligible(holdout_metrics)
    status = "ELIGIBLE" if eligible else "NOT_ELIGIBLE"

    output_dir.mkdir(parents=True, exist_ok=True)
    dataset_path = output_dir / "synthetic-samples.csv"
    model_path = output_dir / "xgboost-model.json"
    metadata_path = output_dir / "metadata.json"
    metrics_path = output_dir / "metrics.json"
    hashes_path = output_dir / "sha256.json"

    write_dataset(dataset_path, samples)
    booster.save_model(model_path)
    model_digest = _sha256(model_path)

    metadata = {
        "schemaVersion": 1,
        "version": f"xgb-p4-seed{SEED}-{model_digest[:12]}",
        "modelKey": "paper-score-p4",
        "artifactUri": "algorithm-service://artifacts/xgboost-model.json",
        "status": status,
        "modelType": "xgboost-regression",
        "modelFormat": "xgboost-json",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "seed": SEED,
        "ruleVersion": RULE_VERSION,
        "standardGroupSize": 4,
        "featureOrder": FEATURE_ORDER,
        "featureSchema": [
            {
                "name": "contribution",
                "jsonField": "contribution",
                "type": "float64",
                "minimum": 0.0,
                "maximum": 1.0,
            },
            {
                "name": "actual_group_size",
                "jsonField": "actualGroupSize",
                "type": "int32",
                "minimum": 1,
                "maximum": 5,
            },
            {
                "name": "group_score",
                "jsonField": "groupScore",
                "type": "float64",
                "minimum": 0.0,
                "maximum": 100.0,
            },
        ],
        "gate": {"jsonField": "standardGroupSize", "equals": 4},
        "datasetRows": len(samples),
        "trainingRows": len(training_indices),
        "testRows": len(test_indices),
        "datasetSha256": dataset_sha256(samples),
        "modelSha256": model_digest,
        "parameters": parameters,
        "eligibilityThresholds": ELIGIBILITY_THRESHOLDS,
        "split": {
            "method": "deterministic_stratified_by_actual_group_size",
            "holdoutFraction": HOLDOUT_FRACTION,
            "distribution": split_distribution,
        },
        "crossValidation": {
            "method": "deterministic_stratified_round_robin",
            "folds": CV_FOLDS,
        },
        "search": search_metadata,
    }
    metrics = {
        "status": status,
        "eligibility": {
            "passed": eligible,
            "thresholds": ELIGIBILITY_THRESHOLDS,
        },
        "holdout": holdout_metrics,
        "crossValidation": cross_validation,
        "note": "Metrics measure fit to deterministic rule-generated labels.",
    }
    _write_json(metadata_path, metadata)
    _write_json(metrics_path, metrics)

    hashes = {
        "algorithm": "sha256",
        "files": {
            path.name: _sha256(path)
            for path in (dataset_path, model_path, metadata_path, metrics_path)
        },
    }
    _write_json(hashes_path, hashes)
    print(
        json.dumps(
            {
                "outputDir": str(output_dir),
                "version": metadata["version"],
                "status": status,
                "holdout": holdout_metrics,
                "crossValidation": cross_validation["summary"],
                "search": search_metadata,
            },
            indent=2,
        )
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=Path("artifacts"))
    parser.add_argument(
        "--search",
        action="store_true",
        help="run reproducible BayesSearchCV before final training",
    )
    parser.add_argument("--search-iterations", type=int, default=30)
    args = parser.parse_args()
    if args.search_iterations < 1:
        parser.error("--search-iterations must be at least 1")
    train(args.output_dir, args.search, args.search_iterations)


if __name__ == "__main__":
    main()
