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
    @ValueSource(strings = {"MODEL_COMPLETE", "DEGRADED", "PENDING_REVIEW", "FAILED"})
    void rejectsPublicationUntilTheWholeRunIsApproved(String status) {
        when(mapper.lockTeacherRunStatus(10, 1)).thenReturn(status);
        assertThatThrownBy(() -> service.publish(teacher, 10)).isInstanceOfSatisfying(
                BusinessException.class, ex -> org.assertj.core.api.Assertions.assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(mapper, never()).publishRun(anyLong(), anyLong());
        verifyNoInteractions(audit);
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
    void gradingDoesNotTurnInvalidScoringInputsIntoPublishedFallbackScores() {
        when(mapper.gradeSubmission(11, 1, 80, null)).thenReturn(1);
        assertThatThrownBy(() -> service.gradeSubmission(teacher, 11, new GradeSubmissionRequest(80, null)))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).projectIndividualScores(anyLong());
        verify(mapper, never()).publishRun(anyLong(), anyLong());
    }

    @Test
    void reviewedRunCannotBeChangedByAStaleReviewRequest() {
        when(mapper.lockTeacherRunStatus(10, 1)).thenReturn("APPROVED");
        assertThatThrownBy(() -> service.reviewAndApprove(teacher, 10,
                new ScoreReviewRequest(List.of(new ScoreReviewRequest.Decision(100, 80.0, null)))))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).reviewResult(anyLong(), anyLong(), anyLong(), anyDouble(), any());
    }
}
