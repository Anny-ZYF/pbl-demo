package com.gdmu.scoring;

import com.gdmu.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaperScoringEngineTest {
    private final PaperScoringEngine engine = new PaperScoringEngine();

    @Test
    void calculatesMcqsContributionFromBranchWeightsAndMemberShares() {
        var result = engine.contributions(List.of(
                new PaperScoringEngine.Branch(1, 0.4, List.of(
                        new PaperScoringEngine.MemberShare(10, 0.75),
                        new PaperScoringEngine.MemberShare(20, 0.25))),
                new PaperScoringEngine.Branch(2, 0.6, List.of(
                        new PaperScoringEngine.MemberShare(10, 0.5),
                        new PaperScoringEngine.MemberShare(20, 0.5)))
        ));

        assertThat(result).extracting(PaperScoringEngine.MemberContribution::studentId)
                .containsExactly(10L, 20L);
        assertThat(result.get(0).contribution()).isCloseTo(0.6, within(1e-12));
        assertThat(result.get(1).contribution()).isCloseTo(0.4, within(1e-12));
        assertThat(result.stream().mapToDouble(PaperScoringEngine.MemberContribution::contribution).sum())
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void rejectsBranchSharesThatDoNotSumToOne() {
        assertThatThrownBy(() -> engine.contributions(List.of(
                new PaperScoringEngine.Branch(1, 1.0, List.of(
                        new PaperScoringEngine.MemberShare(10, 0.3),
                        new PaperScoringEngine.MemberShare(20, 0.3))))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("占比之和必须为1");
    }

    @Test
    void thresholdUsesInverseOfStandardGroupSize() {
        var score = engine.calculate(4, 80, 0.25);

        assertThat(score.threshold()).isCloseTo(0.25, within(1e-12));
        assertThat(score.branch()).isEqualTo("LOW_CONTRIBUTION");
        assertThat(score.normalizedContribution()).isCloseTo(1.0, within(1e-12));
        assertThat(score.roundedScore()).isEqualTo(80.0);
    }

    @Test
    void highBranchAddsAtMostTheBoostCap() {
        var score = engine.calculate(4, 50, 1.0);

        assertThat(score.branch()).isEqualTo("HIGH_CONTRIBUTION");
        assertThat(score.normalizedContribution()).isCloseTo(1.0, within(1e-12));
        assertThat(score.roundedScore()).isEqualTo(50.0 + PaperScoringEngine.BOOST_CAP);
    }

    @Test
    void p1NeverDividesByZero() {
        var score = engine.calculate(1, 100, 1.0);

        assertThat(score.branch()).isEqualTo("LOW_CONTRIBUTION");
        assertThat(score.roundedScore()).isEqualTo(100.0);
    }

    @Test
    void allScoresStayWithinTheDeclaredRange() {
        for (int p = 1; p <= 4; p++) {
            for (int g = 0; g <= 100; g += 5) {
                for (int x = 0; x <= 20; x++) {
                    double score = engine.calculate(p, g, x / 20.0).rawScore();
                    assertThat(score).isBetween(PaperScoringEngine.MIN_SCORE, PaperScoringEngine.MAX_SCORE);
                }
            }
        }
        // G=100 in the high branch would exceed 100 without the clamp.
        assertThat(engine.calculate(4, 100, 0.9).rawScore()).isEqualTo(100.0);
        assertThat(engine.calculate(3, 0, 0).rawScore()).isEqualTo(0.0);
    }

    @Test
    void rejectsInputsOutsideDeclaredRanges() {
        assertThatThrownBy(() -> engine.calculate(0, 80, 0.2))
                .isInstanceOf(BusinessException.class).hasMessageContaining("标准组规模");
        assertThatThrownBy(() -> engine.calculate(4, 101, 0.2))
                .isInstanceOf(BusinessException.class).hasMessageContaining("0到100");
        assertThatThrownBy(() -> engine.calculate(4, 80, 1.2))
                .isInstanceOf(BusinessException.class).hasMessageContaining("0到1");
        assertThatThrownBy(() -> engine.calculate(4, Double.NaN, 0.2))
                .isInstanceOf(BusinessException.class).hasMessageContaining("0到100");
    }

    @ParameterizedTest(name = "p={0}, G={1}, x={2} -> {3}")
    @CsvSource({
            "4, 80, 0.1, 28.160000000000",
            "4, 80, 0.25, 80.000000000000",
            "4, 80, 0.5, 85.185185185185",
            "2, 60, 0.6, 62.080000000000",
            "1, 100, 1.0, 100.000000000000",
            "3, 0, 0.0, 0.000000000000",
            "4, 100, 0.9, 100.000000000000",
            "2, 0, 0.9, 17.920000000000",
            "3, 75, 0.4, 75.560000000000",
            "4, 50, 1.0, 70.000000000000"
    })
    void matchesSharedJavaPythonGoldenVectors(int p, double g, double x, double expected) {
        assertThat(engine.calculate(p, g, x).rawScore()).isCloseTo(expected, within(1e-9));
    }

    @Test
    void ruleVersionIsTheSmoothstepVersion() {
        assertThat(PaperScoringEngine.RULE_VERSION).isEqualTo("smoothstep-v1");
        assertThat(engine.calculate(2, 60, 0.6).ruleVersion()).isEqualTo("smoothstep-v1");
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
