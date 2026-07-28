from __future__ import annotations

from math import isclose

from app.rules import (
    baseline_score,
    compute_contributions,
    contribution_threshold,
    individual_score,
)


def test_mcqs_contributions_sum_to_one() -> None:
    contributions = compute_contributions(
        [0.4, 0.6],
        [
            [0.5, 0.7],
            [0.5, 0.3],
        ],
    )
    assert contributions == [0.62, 0.38]
    assert isclose(sum(contributions), 1.0)


def test_zero_contribution_uses_p4_low_branch_transform() -> None:
    base = baseline_score(4, 4, 80.0)
    assert isclose(individual_score(4, 4, 80.0, 0.0), base * 3.0 * 0.12)


def test_zero_contribution_without_p4_transform_is_zero() -> None:
    assert individual_score(3, 3, 80.0, 0.0) == 0.0


def test_threshold_contribution_equals_baseline() -> None:
    threshold = contribution_threshold(4)
    expected = baseline_score(4, 4, 80.0)
    assert individual_score(4, 4, 80.0, threshold) == expected


def test_p4_adjustments_apply_to_both_branches() -> None:
    base = baseline_score(4, 4, 80.0)
    low_effective = 0.67 * 0.1 + 0.12
    high_effective = 0.54 * 0.5 + 0.17
    expected_low = base * 6.0 * low_effective / 2.0
    expected_high = base + (100.0 - base) * (high_effective * 6.0 - 2.0) / 4.0
    assert isclose(individual_score(4, 4, 80.0, 0.1), expected_low)
    assert isclose(individual_score(4, 4, 80.0, 0.5), expected_high)


def test_adjusted_score_is_bounded_to_declared_range() -> None:
    score = individual_score(4, 4, 100.0, contribution_threshold(4) - 1e-6)
    assert 0.0 <= score <= 100.0
