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
    void appliesPrintedBaselineAtThreshold() {
        double threshold = 2.0 / 6.0;
        var score = engine.calculate(4, 4, 80, threshold);

        assertThat(score.branch()).isEqualTo("BASELINE");
        assertThat(score.baseline()).isCloseTo(80 + Math.sin(Math.PI * 0.8), within(1e-9));
        assertThat(score.roundedScore()).isEqualTo(80.59);
    }

    @Test
    void appliesP4AffineTransformsAcrossLowAndHighIntervals() {
        var zeroContribution = engine.calculate(4, 4, 80, 0);
        var fullContribution = engine.calculate(4, 4, 80, 1);

        assertThat(zeroContribution.effectiveContribution()).isEqualTo(0.12);
        assertThat(zeroContribution.roundedScore()).isEqualTo(29.01);
        assertThat(fullContribution.effectiveContribution()).isCloseTo(0.71, within(1e-12));
        assertThat(fullContribution.roundedScore()).isEqualTo(91.56);
    }

    @Test
    void clampsScoresAndRejectsOutOfPaperGroupSize() {
        assertThat(engine.calculate(3, 3, 100, 1).roundedScore()).isEqualTo(100);
        assertThatThrownBy(() -> engine.calculate(4, 6, 80, 0.2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("实际组规模");
    }

    @ParameterizedTest(name = "p={0}, r={1}, f={2}, c={3}")
    @CsvSource({
            "4, 4, 80, 0, 29.011602690825",
            "3, 3, 80, 0, 0",
            "4, 2, 80, 0.62, 93.876137708057",
            "4, 4, 80, 0.3333333333333333, 80.587785252292",
            "3, 2, 75, 0.4, 82.071067811865",
            "4, 5, 100, 0.3, 96.3"
    })
    void matchesSharedJavaPythonGoldenVectors(int p, int r, double f, double c, double expected) {
        assertThat(engine.calculate(p, r, f, c).rawScore()).isCloseTo(expected, within(1e-9));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
