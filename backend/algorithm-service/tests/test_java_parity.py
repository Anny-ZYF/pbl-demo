"""Golden vectors shared with the Spring implementation."""

from __future__ import annotations

import pytest

from app.rules import individual_score


@pytest.mark.parametrize(
    ("standard_size", "actual_size", "group_score", "contribution", "expected"),
    [
        (4, 4, 80.0, 0.0, 29.011602690825),
        (3, 3, 80.0, 0.0, 0.0),
        (4, 2, 80.0, 0.62, 93.876137708057),
        (4, 4, 80.0, 2.0 / 6.0, 80.587785252292),
        (3, 2, 75.0, 0.4, 82.071067811865),
        (4, 5, 100.0, 0.3, 96.3),
    ],
)
def test_matches_java_rule_engine_golden_vectors(
    standard_size: int,
    actual_size: int,
    group_score: float,
    contribution: float,
    expected: float,
) -> None:
    assert individual_score(
        standard_size, actual_size, group_score, contribution
    ) == pytest.approx(expected, abs=1e-9)


def test_near_threshold_uses_the_same_baseline_tolerance_as_java() -> None:
    assert individual_score(4, 4, 80.0, 0.333333) == pytest.approx(
        individual_score(4, 4, 80.0, 2.0 / 6.0), abs=1e-9
    )
