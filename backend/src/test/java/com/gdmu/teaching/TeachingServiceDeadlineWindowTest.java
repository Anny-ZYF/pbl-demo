package com.gdmu.teaching;

import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two-day team-formation phase has been removed. Grouping, workload allocation, attachments
 * and submission all share one window: publication .. deadline.
 */
class TeachingServiceDeadlineWindowTest {
    private static final String KEY = "2026/11/student/9/7fb0c3f0-1b2a-4c3d-8e5f-6a7b8c9d0e1f.pdf";
    private final TeachingMapper mapper = mock(TeachingMapper.class);
    private final TeachingService service = new TeachingService(mapper, mock(AuditService.class));
    private final UserPrincipal student = new UserPrincipal(9, "S9", "Student 9", Role.STUDENT, "", true, false, 0);

    private void openWindow() {
        when(mapper.assignmentDeadline(11)).thenReturn(LocalDateTime.now().plusDays(1));
        when(mapper.expiredAssignmentCount(11)).thenReturn(0);
    }

    @Test
    void aMemberCanSubmitImmediatelyAfterJoiningWithoutWaitingForAFormationPhase() {
        openWindow();
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.latestSubmissionId(11, 21)).thenReturn(null);
        when(mapper.lockSubmission(11, 21)).thenReturn(null);

        service.submit(student, 11, new StudentSubmissionRequest("正文", null, null, List.of(), null));

        verify(mapper).upsertSubmission(eq(11L), eq(21L), eq(9L), any());
        verify(mapper, never()).assignmentPublishedAt(anyLong());
    }

    @Test
    void submissionIsRefusedOnceTheDeadlineHasPassed() {
        openWindow();
        when(mapper.expiredAssignmentCount(11)).thenReturn(1);
        when(mapper.studentGroup(11, 9)).thenReturn(21L);

        assertThatThrownBy(() -> service.submit(student, 11,
                new StudentSubmissionRequest("正文", null, null, List.of(), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已截止");
        verify(mapper, never()).upsertSubmission(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void allStudentWritesFreezeAtTheDeadline() {
        when(mapper.expiredAssignmentCount(11)).thenReturn(1);
        when(mapper.assignmentCourseForStudent(11, 9)).thenReturn(3L);
        when(mapper.studentAssignmentGroupSize(11)).thenReturn(4);
        when(mapper.studentGroup(11, 9)).thenReturn(21L);

        assertThatThrownBy(() -> service.createStudentGroup(student, 11))
                .isInstanceOf(BusinessException.class).hasMessageContaining("作业已截止");
        assertThatThrownBy(() -> service.joinStudentGroup(student, 11, 1))
                .isInstanceOf(BusinessException.class).hasMessageContaining("作业已截止");
        assertThatThrownBy(() -> service.leaveStudentGroup(student, 11))
                .isInstanceOf(BusinessException.class).hasMessageContaining("作业已截止");
        assertThatThrownBy(() -> service.registerDraftAttachment(student, 11,
                new DraftAttachmentRequest(KEY, "附件.pdf", 1L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("作业已截止");
        assertThatThrownBy(() -> service.deleteStudentAttachment(student, 11, 5L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("作业已截止");

        verify(mapper, never()).insertStudentGroup(anyString(), anyLong(), anyLong(), anyLong(), anyInt());
        verify(mapper, never()).ensureDraftSubmission(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).deleteStudentAttachment(anyLong(), anyLong(), anyLong());
    }
}
