"""Golden vectors shared with the Spring implementation.

Both sides must stay byte-identical: PaperScoringEngineTest.java carries the
same list. Keep the two lists in sync when the rule changes.
"""

from __future__ import annotations

import pytest

from app.rules import individual_score


@pytest.mark.parametrize(
    ("standard_size", "group_score", "contribution", "expected"),
    [
        (4, 80.0, 0.1, 28.160000000000),
        (4, 80.0, 0.25, 80.000000000000),
        (4, 80.0, 0.5, 85.185185185185),
        (2, 60.0, 0.6, 62.080000000000),
        (1, 100.0, 1.0, 100.000000000000),
        (3, 0.0, 0.0, 0.000000000000),
        (4, 100.0, 0.9, 100.000000000000),
        (2, 0.0, 0.9, 17.920000000000),
        (3, 75.0, 0.4, 75.560000000000),
        (4, 50.0, 1.0, 70.000000000000),
    ],
)
def test_matches_java_rule_engine_golden_vectors(
    standard_size: int,
    group_score: float,
    contribution: float,
    expected: float,
) -> None:
    assert individual_score(
        standard_size, group_score, contribution
    ) == pytest.approx(expected, abs=1e-9)


def test_every_golden_vector_stays_in_range() -> None:
    for p in range(1, 5):
        for g in (0.0, 50.0, 100.0):
            for x in (0.0, 0.25, 0.5, 0.9, 1.0):
                assert 0.0 <= individual_score(p, g, x) <= 100.0
