package com.gdmu.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScoringPublicationTest {
    private final ScoringMapper mapper = mock(ScoringMapper.class);
    private final AuditService audit = mock(AuditService.class);
    private final ScoringService service = new ScoringService(mapper, new PaperScoringEngine(),
            mock(ModelClient.class), new ObjectMapper(), audit);
    private final UserPrincipal teacher = new UserPrincipal(1, "teacher", "教师", Role.TEACHER,
            "test-only", true, false, 0);

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_REVIEW", "FAILED"})
    void rejectsPublicationForRunsThatNeverFinishedScoring(String status) {
        when(mapper.lockTeacherRunStatus(10, 1)).thenReturn(status);
        assertThatThrownBy(() -> service.publish(teacher, 10)).isInstanceOfSatisfying(
                BusinessException.class, ex -> org.assertj.core.api.Assertions.assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(mapper, never()).reviewAllResults(anyLong(), anyLong(), anyString());
        verify(mapper, never()).publishRun(anyLong(), anyLong());
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MODEL_COMPLETE", "DEGRADED"})
    void publishingAutoApprovesAlgorithmScoresInsteadOfDemandingManualReview(String status) {
        when(mapper.lockTeacherRunStatus(10, 1)).thenReturn(status);
        when(mapper.reviewAllResults(eq(10L), eq(1L), anyString())).thenReturn(2);
        when(mapper.approveRun(10, 1)).thenReturn(1);
        when(mapper.runResults(10)).thenReturn(List.of(Map.of("id", 100L), Map.of("id", 101L)));
        when(mapper.publishRun(10, 1)).thenReturn(1);
        when(mapper.publishResults(10)).thenReturn(2);

        service.publish(teacher, 10);

        verify(mapper).reviewAllResults(eq(10L), eq(1L), anyString());
        verify(mapper).approveRun(10, 1);
        verify(mapper).projectIndividualScores(10);
        verify(mapper).projectFinalScores(10);
        verify(audit).record(any(), eq("SCORING_RUN_AUTO_APPROVED"), anyString(), any(), anyMap());
        verify(audit).record(any(), eq("SCORES_PUBLISHED"), anyString(), any(), anyMap());
    }

    @Test
    void autoApprovalRefusesARunWithoutAnyDraftResults() {
        when(mapper.lockTeacherRunStatus(10, 1)).thenReturn("MODEL_COMPLETE");
        when(mapper.reviewAllResults(eq(10L), eq(1L), anyString())).thenReturn(0);
        assertThatThrownBy(() -> service.publish(teacher, 10)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).approveRun(anyLong(), anyLong());
        verify(mapper, never()).publishRun(anyLong(), anyLong());
    }

    @Test
    void rejectsAnotherTeachersRunBeforeReadingOrWritingResults() {
        assertThatThrownBy(() -> service.publish(teacher, 10)).isInstanceOfSatisfying(
                BusinessException.class, ex -> org.assertj.core.api.Assertions.assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(mapper).lockTeacherRunStatus(10, 1);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void repeatedPublicationDoesNotTouchHistoryOrProjections() {
        when(mapper.lockTeacherRunStatus(10, 1)).thenReturn("PUBLISHED");
        service.publish(teacher, 10);
        verify(mapper).lockTeacherRunStatus(10, 1);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(audit);
    }

    @Test
    void approvedStatusCannotHideAnUnreviewedMember() {
        when(mapper.lockTeacherRunStatus(10, 1)).thenReturn("APPROVED");
        when(mapper.runResults(10)).thenReturn(List.of(Map.of("id", 100L), Map.of("id", 101L)));
        when(mapper.unreviewedResultCount(10)).thenReturn(1);
        assertThatThrownBy(() -> service.publish(teacher, 10)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).publishRun(anyLong(), anyLong());
    }

    @Test
    void emptyRunCannotBePublished() {
        when(mapper.lockTeacherRunStatus(10, 1)).thenReturn("APPROVED");
        when(mapper.runResults(10)).thenReturn(List.of());
        assertThatThrownBy(() -> service.publish(teacher, 10)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).publishRun(anyLong(), anyLong());
    }

    @Test
    void rejectsPartialResultPublicationSoTheTransactionCanRollBack() {
        when(mapper.lockTeacherRunStatus(10, 1)).thenReturn("APPROVED");
        when(mapper.runResults(10)).thenReturn(List.of(Map.of("id", 100L), Map.of("id", 101L)));
        when(mapper.publishRun(10, 1)).thenReturn(1);
        when(mapper.publishResults(10)).thenReturn(1);
        assertThatThrownBy(() -> service.publish(teacher, 10)).isInstanceOf(BusinessException.class);
        verify(mapper, never()).projectFinalScores(anyLong());
        verifyNoInteractions(audit);
    }

    @Test
    void gradingKeepsTheTeamScoreButNeverPublishesWhenScoringInputsAreInvalid() {
        when(mapper.gradeSubmission(11, 1, 80, null)).thenReturn(1);
        // 录分后立即评分，但前置条件不满足时只跳过评分，团队成绩照常保存，绝不发布兜底成绩。
        var result = service.gradeSubmission(teacher, 11, new GradeSubmissionRequest(80, null));
        org.assertj.core.api.Assertions.assertThat(result).containsEntry("scoringSkipped", true);
        verify(mapper, never()).projectIndividualScores(anyLong());
        verify(mapper, never()).publishRun(anyLong(), anyLong());
    }
}
