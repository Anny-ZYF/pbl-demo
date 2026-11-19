from __future__ import annotations

from math import isclose

import pytest

from app.rules import (
    BOOST_CAP,
    MAX_SCORE,
    MIN_SCORE,
    RULE_VERSION,
    RuleInputError,
    compute_contributions,
    contribution_threshold,
    individual_score,
    score_detail,
    score_group,
)


def test_version_is_the_smoothstep_version() -> None:
    assert RULE_VERSION == "smoothstep-v1"
    assert BOOST_CAP == 20.0


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


def test_threshold_is_inverse_of_standard_group_size() -> None:
    assert contribution_threshold(1) == 1.0
    assert isclose(contribution_threshold(2), 0.5)
    assert isclose(contribution_threshold(4), 0.25)


def test_low_branch_uses_smoothstep_of_x_times_p() -> None:
    detail = score_detail(4, 80.0, 0.1)
    assert detail.branch == "LOW_CONTRIBUTION"
    assert isclose(detail.normalized_contribution, 0.4)
    assert isclose(detail.rule_score, 28.16)


def test_high_branch_caps_the_boost_at_b() -> None:
    detail = score_detail(4, 50.0, 1.0)
    assert detail.branch == "HIGH_CONTRIBUTION"
    assert isclose(detail.normalized_contribution, 1.0)
    assert isclose(detail.rule_score, 50.0 + BOOST_CAP)


def test_threshold_is_continuous_across_branches() -> None:
    threshold = contribution_threshold(4)
    assert individual_score(4, 80.0, threshold) == pytest.approx(80.0)
    assert individual_score(4, 80.0, threshold + 1e-9) == pytest.approx(80.0, abs=1e-6)


def test_p1_never_divides_by_zero() -> None:
    assert individual_score(1, 100.0, 1.0) == 100.0
    assert individual_score(1, 50.0, 0.0) == 0.0


def test_scores_are_always_between_zero_and_one_hundred() -> None:
    for p in range(1, 5):
        for g in range(0, 101, 5):
            for step in range(21):
                score = individual_score(p, float(g), step / 20.0)
                assert MIN_SCORE <= score <= MAX_SCORE
    assert individual_score(4, 100.0, 0.9) == 100.0
    assert individual_score(3, 0.0, 0.0) == 0.0


@pytest.mark.parametrize(
    ("p", "g", "x"),
    [(0, 80.0, 0.2), (5, 80.0, 0.2), (4, 101.0, 0.2), (4, -1.0, 0.2), (4, 80.0, 1.2), (4, 80.0, -0.1)],
)
def test_invalid_inputs_are_rejected(p: int, g: float, x: float) -> None:
    with pytest.raises(RuleInputError):
        individual_score(p, g, x)


def test_score_group_works_without_actual_group_size() -> None:
    results = score_group(2, 60.0, [0.4, 0.6], [[0.75, 0.5], [0.25, 0.5]])
    assert [round(row.contribution, 4) for row in results] == [0.6, 0.4]
    assert all(MIN_SCORE <= row.rule_score <= MAX_SCORE for row in results)
