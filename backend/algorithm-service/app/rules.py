"""Deterministic implementation of the paper's MCQS scoring rules."""

from __future__ import annotations

from dataclasses import dataclass
from math import exp, fsum, isclose, pi, sin
from typing import Sequence


RULE_VERSION = "paper-mcqs-v1"
SUM_TOLERANCE = 1e-6
SCORE_TOLERANCE = 1e-6


class RuleInputError(ValueError):
    """Raised when an input cannot satisfy the paper's scoring constraints."""


@dataclass(frozen=True)
class MemberRuleResult:
    member_index: int
    contribution: float
    baseline_score: float
    rule_score: float


def _validate_group_size(standard_group_size: int, actual_group_size: int) -> None:
    if not 1 <= standard_group_size <= 4:
        raise RuleInputError("standard_group_size must be between 1 and 4")
    if not 1 <= actual_group_size <= standard_group_size + 1:
        raise RuleInputError("actual_group_size must be between 1 and p + 1")


def group_size_coefficient(standard_group_size: int, actual_group_size: int) -> float:
    """Return alpha from formula (2), preserving the paper's p-r=0 branch."""

    _validate_group_size(standard_group_size, actual_group_size)
    delta = standard_group_size - actual_group_size
    if delta == 0:
        return exp(delta)
    if delta in {-1, 1, 2, 3}:
        return 10.0 * delta
    raise RuleInputError("unsupported difference between standard and actual group size")


def baseline_score(
    standard_group_size: int,
    actual_group_size: int,
    group_score: float,
) -> float:
    """Return the group-size-adjusted baseline score from formula (3)."""

    if not 0.0 <= group_score <= 100.0:
        raise RuleInputError("group_score must be between 0 and 100")
    alpha = group_size_coefficient(standard_group_size, actual_group_size)
    return group_score + alpha * sin(pi * group_score / 100.0)


def contribution_threshold(standard_group_size: int) -> float:
    if not 1 <= standard_group_size <= 4:
        raise RuleInputError("standard_group_size must be between 1 and 4")
    return 2.0 / (standard_group_size + 2.0)


def _paper_adjusted_contribution(contribution: float, high_branch: bool) -> float:
    if high_branch:
        return 0.54 * contribution + 0.17
    return 0.67 * contribution + 0.12


def individual_score(
    standard_group_size: int,
    actual_group_size: int,
    group_score: float,
    contribution: float,
) -> float:
    """Return a member score using formulas (5)-(7).

    The two p=4 contribution transforms are applied to their complete branches,
    including c=0 in the low branch. For p!=4, c=0 follows the unadjusted low-
    branch limit and returns zero. Scores are bounded to the paper's declared
    [0, 100] range.
    """

    if not 0.0 <= contribution <= 1.0:
        raise RuleInputError("contribution must be between 0 and 1")

    base = baseline_score(standard_group_size, actual_group_size, group_score)
    threshold = contribution_threshold(standard_group_size)

    if contribution <= SCORE_TOLERANCE and standard_group_size != 4:
        return 0.0
    if isclose(contribution, threshold, abs_tol=SCORE_TOLERANCE, rel_tol=0.0):
        return min(100.0, max(0.0, base))

    if contribution > threshold:
        effective = (
            _paper_adjusted_contribution(contribution, high_branch=True)
            if standard_group_size == 4
            else contribution
        )
        score = (
            (100.0 - base)
            * (effective * (standard_group_size + 2.0) - 2.0)
            / standard_group_size
            + base
        )
    else:
        effective = (
            _paper_adjusted_contribution(contribution, high_branch=False)
            if standard_group_size == 4
            else contribution
        )
        score = base * (standard_group_size + 2.0) * effective / 2.0

    return min(100.0, max(0.0, score))


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
    actual_group_size: int,
    group_score: float,
    branch_weights: Sequence[float],
    workload_matrix: Sequence[Sequence[float]],
) -> list[MemberRuleResult]:
    _validate_group_size(standard_group_size, actual_group_size)
    if len(workload_matrix) != actual_group_size:
        raise RuleInputError("workload row count must equal actual_group_size")

    contributions = compute_contributions(branch_weights, workload_matrix)
    base = baseline_score(standard_group_size, actual_group_size, group_score)
    return [
        MemberRuleResult(
            member_index=index,
            contribution=contribution,
            baseline_score=base,
            rule_score=individual_score(
                standard_group_size,
                actual_group_size,
                group_score,
                contribution,
            ),
        )
        for index, contribution in enumerate(contributions)
    ]
