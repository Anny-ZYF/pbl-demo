package com.gdmu.teaching;

import com.gdmu.audit.AuditService;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.gdmu.common.BusinessException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeachingServiceGroupMembershipTest {
    private final TeachingMapper mapper = mock(TeachingMapper.class);
    private final TeachingService service = new TeachingService(mapper, mock(AuditService.class));
    private final UserPrincipal student = new UserPrincipal(9, "S9", "Student 9", Role.STUDENT, "", true, false, 0);

    @Test
    void leavingLeaderPromotesOldestRemainingMemberAndKeepsHistory() {
        when(mapper.assignmentPublishedAt(11)).thenReturn(LocalDateTime.now().minusHours(1));
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.activeGroupMemberRole(21, 9)).thenReturn("LEADER");
        when(mapper.leaveStudentGroup(21, 9)).thenReturn(1);
        when(mapper.activeGroupMemberCount(21)).thenReturn(1);
        when(mapper.nextGroupLeader(21)).thenReturn(10L);
        when(mapper.promoteGroupLeader(21, 10)).thenReturn(1);

        service.leaveStudentGroup(student, 11);

        var ordered = inOrder(mapper);
        ordered.verify(mapper).leaveStudentGroup(21, 9);
        ordered.verify(mapper).promoteGroupLeader(21, 10);
        ordered.verify(mapper).updateGroupLeader(21, 10);
        ordered.verify(mapper).syncGroupMemberCount(21);
        ordered.verify(mapper).supersedeGroupWorkloads(11, 21);
    }

    @Test
    void joiningFormerGroupReactivatesMembershipInsteadOfInsertingDuplicate() {
        when(mapper.assignmentCourseForStudent(11, 9)).thenReturn(3L);
        when(mapper.findStudentGroup(11, "第1组")).thenReturn(Map.of("id", 21L, "maxMembers", 4));
        when(mapper.studentGroup(11, 9)).thenReturn(null);
        when(mapper.activeGroupMemberCount(21)).thenReturn(2);
        when(mapper.reactivateStudentGroupMember(21, 11, 9)).thenReturn(1);

        service.joinStudentGroup(student, 11, 1);

        var ordered = inOrder(mapper);
        ordered.verify(mapper).reactivateStudentGroupMember(21, 11, 9);
        ordered.verify(mapper).syncGroupMemberCount(21);
        ordered.verify(mapper).supersedeGroupWorkloads(11, 21);
    }

    @Test
    void aStudentCannotLeaveAGradedGroupBeforeTheDeadline() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.gradedGroupSubmission(11, 21)).thenReturn(1);

        assertThrows(BusinessException.class, () -> service.leaveStudentGroup(student, 11));

        verify(mapper, never()).leaveStudentGroup(21, 9);
        verify(mapper, never()).supersedeGroupWorkloads(11, 21);
    }

    @Test
    void creatingGroupUsesMapperProvidedHistorySafeNumber() {
        when(mapper.assignmentCourseForStudent(11, 9)).thenReturn(3L);
        when(mapper.studentAssignmentGroupSize(11)).thenReturn(4);
        when(mapper.studentGroup(11, 9)).thenReturn(null);
        when(mapper.reusableStudentGroup(11)).thenReturn(null);
        when(mapper.nextGroupNo(11)).thenReturn(2);
        when(mapper.lastGroupId()).thenReturn(22L);

        service.createStudentGroup(student, 11);

        verify(mapper).insertStudentGroup("第2组", 3L, 11, 9, 5);
        verify(mapper).insertStudentLeader(22L, 11, 9);
    }

    @Test
    void creatingGroupReusesLowestDissolvedGroupNumber() {
        when(mapper.assignmentCourseForStudent(11, 9)).thenReturn(3L);
        when(mapper.studentAssignmentGroupSize(11)).thenReturn(4);
        when(mapper.studentGroup(11, 9)).thenReturn(null);
        when(mapper.reusableStudentGroup(11)).thenReturn(Map.of("id", 21L, "name", "第1组"));
        when(mapper.reactivateStudentGroup(21, 9, 5)).thenReturn(1);

        service.createStudentGroup(student, 11);

        verify(mapper).reactivateStudentGroup(21, 9, 5);
        verify(mapper).upsertStudentLeader(21, 11, 9);
    }

    @Test
    void regularMemberCannotSubmitOnBehalfOfTheGroup() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.assignmentDeadline(11)).thenReturn(LocalDateTime.now().plusDays(1));
        when(mapper.activeGroupMemberRole(21, 9)).thenReturn("MEMBER");
        StudentSubmissionRequest request = new StudentSubmissionRequest(
                "作业正文", null, null, List.of(), Map.of(9L, BigDecimal.valueOf(100)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submit(student, 11, request));

        assertEquals("只有小组组长可以编辑并提交作业", exception.getMessage());
    }

    @Test
    void submitIsBlockedUntilTheGroupHasALockedWorkloadVersion() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.assignmentDeadline(11)).thenReturn(LocalDateTime.now().plusDays(1));
        when(mapper.activeGroupMemberRole(21, 9)).thenReturn("LEADER");
        when(mapper.lockedWorkloadCount(11, 21)).thenReturn(0);
        StudentSubmissionRequest request = new StudentSubmissionRequest(
                "作业正文", null, null, List.of(), Map.of(9L, BigDecimal.valueOf(100)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submit(student, 11, request));

        assertEquals("请先在「小组分工」中填写并保存贡献度，再提交作业", exception.getMessage());
        verify(mapper, never()).upsertSubmission(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void legacyContributionScoresNoLongerCreateAWorkloadVersionOnSubmit() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.assignmentDeadline(11)).thenReturn(LocalDateTime.now().plusDays(1));
        when(mapper.activeGroupMemberRole(21, 9)).thenReturn("LEADER");
        when(mapper.lockedWorkloadCount(11, 21)).thenReturn(1);
        when(mapper.lockSubmission(11, 21)).thenReturn(null);
        StudentSubmissionRequest request = new StudentSubmissionRequest(
                "作业正文", null, null, List.of(), Map.of(9L, BigDecimal.valueOf(100)));

        service.submit(student, 11, request);

        // 分工只由「小组分工」面板写入；提交请求里的旧贡献度不再落库，也不会作废现有分工版本。
        verify(mapper).upsertSubmission(eq(11L), eq(21L), eq(9L), any());
        verify(mapper, never()).supersedeGroupWorkloads(11, 21);
    }
}
