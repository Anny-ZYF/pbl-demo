package com.gdmu.scoring;

import com.gdmu.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

@Component
public class PaperScoringEngine {
    public static final String RULE_VERSION = "paper-mcqs-v1";
    private static final double EPSILON = 1e-6;

    public Score calculate(int standardGroupSize, int actualGroupSize, double groupScore, double contribution) {
        validate(standardGroupSize, actualGroupSize, groupScore, contribution);
        int difference = standardGroupSize - actualGroupSize;
        double alpha = difference == 0 ? Math.exp(0) : 10.0 * difference;
        double baseline = groupScore + alpha * Math.sin(Math.PI * groupScore / 100.0);
        double threshold = 2.0 / (standardGroupSize + 2.0);
        double raw;
        String branch;
        double effectiveContribution = contribution;
        if (Math.abs(contribution - threshold) <= EPSILON) {
            raw = baseline;
            branch = "BASELINE";
        } else if (contribution > threshold) {
            if (standardGroupSize == 4) {
                effectiveContribution = 0.54 * contribution + 0.17;
            }
            raw = ((100.0 - baseline) * (effectiveContribution * (standardGroupSize + 2.0) - 2.0)
                    / standardGroupSize) + baseline;
            branch = "HIGH_CONTRIBUTION";
        } else {
            if (standardGroupSize == 4) {
                effectiveContribution = 0.67 * contribution + 0.12;
            }
            raw = baseline * (standardGroupSize + 2.0) * effectiveContribution / 2.0;
            branch = "LOW_CONTRIBUTION";
        }
        double clamped = Math.max(0, Math.min(100, raw));
        return new Score(RULE_VERSION, baseline, threshold, contribution, effectiveContribution,
                clamped, round2(clamped), branch, standardGroupSize == 4);
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

    private void validate(int p, int r, double f, double c) {
        if (p < 1 || p > 4) throw invalid("标准组规模必须在1到4之间");
        if (r < 1 || r > p + 1) throw invalid("实际组规模必须在1到标准组规模+1之间");
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
    public record Score(String ruleVersion, double baseline, double threshold, double contribution,
                        double effectiveContribution, double rawScore, double roundedScore,
                        String branch, boolean withinPaperModelScope) { }
}
