package com.gdmu.scoring;

import com.gdmu.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

/**
 * Deterministic smoothstep scoring rule (version {@code smoothstep-v1}).
 *
 * <p>The class name is kept for Spring wiring compatibility; the implementation
 * is the formula from the project's model generation script (1108.py), not the
 * earlier printed-paper MCQS formulas. The arithmetic order matches
 * {@code app/rules.py} so both implementations produce identical scores.
 */
@Component
public class PaperScoringEngine {
    public static final String RULE_VERSION = "smoothstep-v1";
    public static final double BOOST_CAP = 20.0;
    public static final double MIN_SCORE = 0.0;
    public static final double MAX_SCORE = 100.0;
    private static final double EPSILON = 1e-6;

    public Score calculate(int standardGroupSize, double groupScore, double contribution) {
        validate(standardGroupSize, groupScore, contribution);
        double threshold = 1.0 / standardGroupSize;
        double normalized;
        double raw;
        String branch;
        if (contribution <= threshold) {
            // Low branch: t = x * p, score = G * S(t).
            normalized = contribution * standardGroupSize;
            raw = groupScore * smoothstep(normalized);
            branch = "LOW_CONTRIBUTION";
        } else {
            // High branch: t = (x - 1/p) / (1 - 1/p), score = G + B * S(t).
            // p == 1 can never reach here because contribution <= 1 == 1/p.
            normalized = (contribution - threshold) / (1.0 - threshold);
            raw = groupScore + BOOST_CAP * smoothstep(normalized);
            branch = "HIGH_CONTRIBUTION";
        }
        double clamped = Math.max(MIN_SCORE, Math.min(MAX_SCORE, raw));
        return new Score(RULE_VERSION, threshold, normalized, branch, clamped, round2(clamped));
    }

    private double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    public List<MemberContribution> contributions(List<Branch> branches) {
        if (branches == null || branches.isEmpty()) {
            throw invalid("至少需要一个评价分支");
        }
        double weightSum = branches.stream().mapToDouble(Branch::weight).sum();
        if (Math.abs(weightSum - 1.0) > EPSILON) {
            throw invalid("评价分支权重之和必须为1");
        }
        Collection<Long> memberIds = branches.getFirst().shares().stream().map(MemberShare::studentId).toList();
        if (memberIds.isEmpty()) {
            throw invalid("团队至少需要一名成员");
        }
        for (Branch branch : branches) {
            if (branch.weight() < 0 || branch.weight() > 1) {
                throw invalid("分支权重必须在0到1之间");
            }
            double shareSum = branch.shares().stream().mapToDouble(MemberShare::share).sum();
            if (Math.abs(shareSum - 1.0) > EPSILON) {
                throw invalid("每个评价分支的成员工作量占比之和必须为1");
            }
            List<Long> currentIds = branch.shares().stream().map(MemberShare::studentId).sorted().toList();
            if (!currentIds.equals(memberIds.stream().sorted().toList())) {
                throw invalid("每个分支必须包含完全相同的团队成员");
            }
        }
        return memberIds.stream().map(studentId -> new MemberContribution(studentId,
                branches.stream().mapToDouble(branch -> branch.weight() * branch.shares().stream()
                        .filter(share -> share.studentId() == studentId).findFirst().orElseThrow().share()).sum())).toList();
    }

    private void validate(int p, double f, double c) {
        if (p < 1 || p > 4) throw invalid("标准组规模必须在1到4之间");
        if (!Double.isFinite(f) || f < 0 || f > 100) throw invalid("团队作品分必须在0到100之间");
        if (!Double.isFinite(c) || c < 0 || c > 1) throw invalid("贡献度必须在0到1之间");
    }

    private BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public record MemberShare(long studentId, double share) { }
    public record Branch(long partId, double weight, List<MemberShare> shares) { }
    public record MemberContribution(long studentId, double contribution) { }
    public record Score(String ruleVersion, double threshold, double normalizedContribution,
                        String branch, double rawScore, double roundedScore) { }
}
