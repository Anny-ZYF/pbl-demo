"""Deterministic smoothstep scoring rule.

Implementation of the formula in the project's model generation script
(1108.py). The arithmetic order is mirrored in the Spring scoring engine so
that both implementations agree on the golden vectors in test_java_parity.py.
"""

from __future__ import annotations

from dataclasses import dataclass
from math import fsum, isclose
from typing import Sequence


RULE_VERSION = "smoothstep-v1"
BOOST_CAP = 20.0
MIN_SCORE = 0.0
MAX_SCORE = 100.0
SUM_TOLERANCE = 1e-6


class RuleInputError(ValueError):
    """Raised when an input cannot satisfy the scoring constraints."""


@dataclass(frozen=True)
class ScoreDetail:
    threshold: float
    normalized_contribution: float
    branch: str
    rule_score: float


@dataclass(frozen=True)
class MemberRuleResult:
    member_index: int
    contribution: float
    threshold: float
    normalized_contribution: float
    branch: str
    rule_score: float


def _validate_group_size(standard_group_size: int) -> None:
    if not 1 <= standard_group_size <= 4:
        raise RuleInputError("standard_group_size must be between 1 and 4")


def _smoothstep(t: float) -> float:
    return t * t * (3.0 - 2.0 * t)


def contribution_threshold(standard_group_size: int) -> float:
    _validate_group_size(standard_group_size)
    return 1.0 / standard_group_size


def score_detail(
    standard_group_size: int,
    group_score: float,
    contribution: float,
) -> ScoreDetail:
    _validate_group_size(standard_group_size)
    if not MIN_SCORE <= group_score <= MAX_SCORE:
        raise RuleInputError("group_score must be between 0 and 100")
    if not 0.0 <= contribution <= 1.0:
        raise RuleInputError("contribution must be between 0 and 1")

    threshold = 1.0 / standard_group_size
    if contribution <= threshold:
        # Low branch: t = x * p, score = G * S(t).
        normalized = contribution * standard_group_size
        raw = group_score * _smoothstep(normalized)
        branch = "LOW_CONTRIBUTION"
    else:
        # High branch: t = (x - 1/p) / (1 - 1/p), score = G + B * S(t).
        # p == 1 never reaches here because contribution <= 1 == 1/p.
        normalized = (contribution - threshold) / (1.0 - threshold)
        raw = group_score + BOOST_CAP * _smoothstep(normalized)
        branch = "HIGH_CONTRIBUTION"

    return ScoreDetail(threshold, normalized, branch, min(MAX_SCORE, max(MIN_SCORE, raw)))


def individual_score(
    standard_group_size: int,
    group_score: float,
    contribution: float,
) -> float:
    return score_detail(standard_group_size, group_score, contribution).rule_score


def compute_contributions(
    branch_weights: Sequence[float],
    workload_matrix: Sequence[Sequence[float]],
) -> list[float]:
    """Calculate C = Q W^T for a member-by-branch workload matrix."""

    if not branch_weights:
        raise RuleInputError("at least one branch weight is required")
    if any(weight < 0.0 for weight in branch_weights):
        raise RuleInputError("branch weights cannot be negative")
    if not isclose(fsum(branch_weights), 1.0, abs_tol=SUM_TOLERANCE, rel_tol=0.0):
        raise RuleInputError("branch weights must sum to 1")
    if not workload_matrix:
        raise RuleInputError("at least one member workload row is required")

    branch_count = len(branch_weights)
    for row in workload_matrix:
        if len(row) != branch_count:
            raise RuleInputError("every workload row must match the branch count")
        if any(value < 0.0 for value in row):
            raise RuleInputError("workload values cannot be negative")

    for branch_index in range(branch_count):
        total = fsum(row[branch_index] for row in workload_matrix)
        if not isclose(total, 1.0, abs_tol=SUM_TOLERANCE, rel_tol=0.0):
            raise RuleInputError(
                f"workload shares for branch {branch_index} must sum to 1"
            )

    contributions = [
        fsum(value * weight for value, weight in zip(row, branch_weights, strict=True))
        for row in workload_matrix
    ]
    if not isclose(fsum(contributions), 1.0, abs_tol=SUM_TOLERANCE, rel_tol=0.0):
        raise RuleInputError("calculated member contributions must sum to 1")
    return contributions


def score_group(
    standard_group_size: int,
    group_score: float,
    branch_weights: Sequence[float],
    workload_matrix: Sequence[Sequence[float]],
) -> list[MemberRuleResult]:
    _validate_group_size(standard_group_size)
    contributions = compute_contributions(branch_weights, workload_matrix)
    results: list[MemberRuleResult] = []
    for index, contribution in enumerate(contributions):
        detail = score_detail(standard_group_size, group_score, contribution)
        results.append(
            MemberRuleResult(
                member_index=index,
                contribution=contribution,
                threshold=detail.threshold,
                normalized_contribution=detail.normalized_contribution,
                branch=detail.branch,
                rule_score=detail.rule_score,
            )
        )
    return results
