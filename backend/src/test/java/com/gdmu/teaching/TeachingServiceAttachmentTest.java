package com.gdmu.teaching;

import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeachingServiceAttachmentTest {
    private static final String KEY = "2026/11/student/9/7fb0c3f0-1b2a-4c3d-8e5f-6a7b8c9d0e1f.pdf";
    private final TeachingMapper mapper = mock(TeachingMapper.class);
    private final TeachingService service = new TeachingService(mapper, mock(AuditService.class));
    private final UserPrincipal student = new UserPrincipal(9, "S9", "Student 9", Role.STUDENT, "", true, false, 0);

    private void givenSubmittableAssignment() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.assignmentPublishedAt(11)).thenReturn(LocalDateTime.now().minusDays(3));
        when(mapper.assignmentDeadline(11)).thenReturn(LocalDateTime.now().plusDays(1));
        when(mapper.lockSubmission(11, 21)).thenReturn(null);
        when(mapper.latestSubmissionId(11, 21)).thenReturn(77L);
    }

    @Test
    void storesTheRealUploadedNameAndSize() {
        givenSubmittableAssignment();
        StudentSubmissionRequest request = new StudentSubmissionRequest("正文", null, null,
                List.of(new SubmissionAttachmentInput(KEY, "团队报告-终稿.pdf", 120045L)), null);

        service.submit(student, 11, request);

        verify(mapper).insertAttachment(77L, KEY, "团队报告-终稿.pdf", 120045L);
    }

    @Test
    void sanitizesClientSuppliedFileNameBeforePersisting() {
        givenSubmittableAssignment();
        StudentSubmissionRequest request = new StudentSubmissionRequest("正文", null, null,
                List.of(new SubmissionAttachmentInput(KEY, "C:\\tmp\\报\"告;.pdf", 12L)), null);

        service.submit(student, 11, request);

        verify(mapper).insertAttachment(77L, KEY, "报告.pdf", 12L);
    }

    @Test
    void legacyStringOnlyAttachmentFallsBackToStorageName() {
        givenSubmittableAssignment();
        StudentSubmissionRequest request = new StudentSubmissionRequest("正文", null, null,
                List.of(SubmissionAttachmentInput.ofKey(KEY)), null);

        service.submit(student, 11, request);

        verify(mapper).insertAttachment(77L, KEY,
                "7fb0c3f0-1b2a-4c3d-8e5f-6a7b8c9d0e1f.pdf", 0L);
    }

    @Test
    void rejectsStorageKeyUploadedByAnotherStudent() {
        givenSubmittableAssignment();
        String foreignKey = "2026/11/student/10/7fb0c3f0-1b2a-4c3d-8e5f-6a7b8c9d0e1f.pdf";
        StudentSubmissionRequest request = new StudentSubmissionRequest("正文", null, null,
                List.of(new SubmissionAttachmentInput(foreignKey, "报告.pdf", 10L)), null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submit(student, 11, request));

        assertEquals("附件路径不合法", exception.getMessage());
    }

    @Test
    void registersUploadedFileAsTeamVisibleDraft() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.latestSubmissionId(11, 21)).thenReturn(77L);

        service.registerDraftAttachment(student, 11,
                new DraftAttachmentRequest(KEY, "小组草稿.pdf", 2048L));

        verify(mapper).ensureDraftSubmission(11, 21L, 9L);
        verify(mapper).insertAttachment(77L, KEY, "小组草稿.pdf", 2048L);
    }

    @Test
    void rejectsDraftAttachmentUploadedByAnotherStudent() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        String foreignKey = "2026/11/student/10/7fb0c3f0-1b2a-4c3d-8e5f-6a7b8c9d0e1f.pdf";

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.registerDraftAttachment(student, 11,
                        new DraftAttachmentRequest(foreignKey, "报告.pdf", 10L)));

        assertEquals("附件必须由当前学生账号上传", exception.getMessage());
    }

    @Test
    void removesAnAttachmentThroughTheResolvedGroupBeforeGrading() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.deleteStudentAttachment(5L, 11L, 21L)).thenReturn(1);

        service.deleteStudentAttachment(student, 11, 5L);

        verify(mapper).deleteStudentAttachment(5L, 11L, 21L);
    }

    @Test
    void refusesAttachmentRemovalForAStudentWithoutAGroup() {
        when(mapper.studentGroup(11, 9)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteStudentAttachment(student, 11, 5L));

        assertEquals("当前学生不属于该作业的有效团队", exception.getMessage());
        verify(mapper, never()).deleteStudentAttachment(anyLong(), anyLong(), anyLong());
    }

    @Test
    void reportsGradedAttachmentsAsLocked() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        // 0 rows: graded / published / another group's attachment.
        when(mapper.deleteStudentAttachment(5L, 11L, 21L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteStudentAttachment(student, 11, 5L));

        assertEquals("附件不存在、不属于本组，或作业已批阅/成绩已发布，不能删除", exception.getMessage());
    }

    @Test
    void attachmentOnlyTeamCanResubmitWithoutUploadingEverythingAgain() {
        givenSubmittableAssignment();
        when(mapper.attachmentCount(77L)).thenReturn(2);
        StudentSubmissionRequest request = new StudentSubmissionRequest("", null, null, List.of(), null);

        service.submit(student, 11, request);

        verify(mapper).upsertSubmission(11L, 21L, 9L, request);
    }

    @Test
    void resubmissionFailsWhenEveryAttachmentWasRemovedAndThereIsNoText() {
        givenSubmittableAssignment();
        when(mapper.attachmentCount(77L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submit(student, 11,
                        new StudentSubmissionRequest("", null, null, List.of(), null)));

        assertEquals("请填写提交说明或上传附件", exception.getMessage());
    }

    @Test
    void aSubmitNoteAloneIsAValidSubmissionWithoutAnyAttachment() {
        givenSubmittableAssignment();
        when(mapper.attachmentCount(77L)).thenReturn(0);
        StudentSubmissionRequest request = new StudentSubmissionRequest("", null, "本次完成 KNN 实验", List.of(), null);

        service.submit(student, 11, request);

        // The note is stored as content too, so teachers and older readers see the same text.
        verify(mapper).upsertSubmission(eq(11L), eq(21L), eq(9L),
                argThat(saved -> "本次完成 KNN 实验".equals(saved.content())));
    }

    @Test
    void aNonBlankLegacyContentIsNotOverwrittenByTheNote() {
        givenSubmittableAssignment();
        StudentSubmissionRequest request = new StudentSubmissionRequest("旧客户端正文", null, "说明", List.of(), null);

        service.submit(student, 11, request);

        verify(mapper).upsertSubmission(eq(11L), eq(21L), eq(9L),
                argThat(saved -> "旧客户端正文".equals(saved.content())));
    }

    @Test
    void aBlankNoteWithoutAttachmentsIsStillRejected() {
        givenSubmittableAssignment();
        when(mapper.attachmentCount(77L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submit(student, 11,
                        new StudentSubmissionRequest("", null, "   ", List.of(), null)));

        assertEquals("请填写提交说明或上传附件", exception.getMessage());
    }
}
