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
    void regularMemberCannotEditContributionScoresDuringSubmission() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.assignmentPublishedAt(11)).thenReturn(LocalDateTime.now().minusDays(3));
        when(mapper.assignmentDeadline(11)).thenReturn(LocalDateTime.now().plusDays(1));
        when(mapper.activeGroupMemberRole(21, 9)).thenReturn("MEMBER");
        StudentSubmissionRequest request = new StudentSubmissionRequest(
                "作业正文", null, null, List.of(), Map.of(9L, BigDecimal.valueOf(100)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.submit(student, 11, request));

        assertEquals("只有小组组长可以编辑贡献度", exception.getMessage());
    }

    @Test
    void existingWorkloadVersionIsNotOverwrittenByLegacyContributionScores() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.assignmentPublishedAt(11)).thenReturn(LocalDateTime.now().minusDays(3));
        when(mapper.assignmentDeadline(11)).thenReturn(LocalDateTime.now().plusDays(1));
        when(mapper.usableWorkloadSetCount(21, 11)).thenReturn(1);
        when(mapper.lockSubmission(11, 21)).thenReturn(null);
        StudentSubmissionRequest request = new StudentSubmissionRequest(
                "作业正文", null, null, List.of(), Map.of(9L, BigDecimal.valueOf(100)));

        service.submit(student, 11, request);

        verify(mapper, never()).nextSubmissionWorkloadVersion(21, 11);
        verify(mapper, never()).supersedeSubmissionWorkloads(21, 11);
        verify(mapper, never()).insertSubmissionWorkloadSet(21, 11, 1, 9);
    }

    @Test
    void legacyContributionScoresStillApplyWhenNoWorkloadVersionExists() {
        when(mapper.studentGroup(11, 9)).thenReturn(21L);
        when(mapper.assignmentPublishedAt(11)).thenReturn(LocalDateTime.now().minusDays(3));
        when(mapper.assignmentDeadline(11)).thenReturn(LocalDateTime.now().plusDays(1));
        when(mapper.usableWorkloadSetCount(21, 11)).thenReturn(0);
        when(mapper.activeGroupMemberRole(21, 9)).thenReturn("LEADER");
        when(mapper.activeGroupStudentIds(21, 11)).thenReturn(List.of(9L));
        when(mapper.nextSubmissionWorkloadVersion(21, 11)).thenReturn(1);
        when(mapper.submissionWorkloadSetId(21, 11, 1)).thenReturn(55L);
        when(mapper.assignmentPartIds(11)).thenReturn(List.of(101L));
        when(mapper.lockSubmission(11, 21)).thenReturn(null);
        StudentSubmissionRequest request = new StudentSubmissionRequest(
                "作业正文", null, null, List.of(), Map.of(9L, BigDecimal.valueOf(100)));

        service.submit(student, 11, request);

        verify(mapper).nextSubmissionWorkloadVersion(21, 11);
        verify(mapper).insertSubmissionWorkloadSet(21, 11, 1, 9);
        verify(mapper).insertSubmissionWorkloadItem(55L, 101L, 9L, BigDecimal.valueOf(1).setScale(6));
    }
}
