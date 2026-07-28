"""Deterministic reconstruction of the paper's 1,785 synthetic samples."""

from __future__ import annotations

import csv
import hashlib
import io
from dataclasses import asdict, dataclass
from pathlib import Path

from app.rules import baseline_score, individual_score


DATASET_COLUMNS = (
    "sample_id",
    "standard_group_size",
    "actual_group_size",
    "contribution",
    "group_score",
    "baseline_score",
    "individual_score",
)


@dataclass(frozen=True)
class SyntheticSample:
    sample_id: int
    standard_group_size: int
    actual_group_size: int
    contribution: float
    group_score: float
    baseline_score: float
    individual_score: float


def generate_paper_samples() -> list[SyntheticSample]:
    """Generate the p=4 grid described in the paper in a stable order."""

    samples: list[SyntheticSample] = []
    score_values = [float(index * 5) for index in range(21)]
    contribution_values = [index / 20.0 for index in range(21)]

    for actual_group_size in range(1, 6):
        contributions = [1.0] if actual_group_size == 1 else contribution_values
        for contribution in contributions:
            for group_score in score_values:
                samples.append(
                    SyntheticSample(
                        sample_id=len(samples) + 1,
                        standard_group_size=4,
                        actual_group_size=actual_group_size,
                        contribution=contribution,
                        group_score=group_score,
                        baseline_score=baseline_score(4, actual_group_size, group_score),
                        individual_score=individual_score(
                            4,
                            actual_group_size,
                            group_score,
                            contribution,
                        ),
                    )
                )
    return samples


def _csv_value(value: object) -> object:
    if isinstance(value, float):
        return f"{value:.12f}"
    return value


def dataset_csv_bytes(samples: list[SyntheticSample]) -> bytes:
    buffer = io.StringIO(newline="")
    writer = csv.DictWriter(buffer, fieldnames=DATASET_COLUMNS, lineterminator="\n")
    writer.writeheader()
    for sample in samples:
        writer.writerow({key: _csv_value(value) for key, value in asdict(sample).items()})
    return buffer.getvalue().encode("utf-8")


def dataset_sha256(samples: list[SyntheticSample]) -> str:
    return hashlib.sha256(dataset_csv_bytes(samples)).hexdigest()


def write_dataset(path: Path, samples: list[SyntheticSample] | None = None) -> str:
    rows = samples if samples is not None else generate_paper_samples()
    content = dataset_csv_bytes(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(content)
    return hashlib.sha256(content).hexdigest()

