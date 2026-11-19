package com.gdmu.teaching;

import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeachingServiceAssignmentEditTest {
    private static final long ASSIGNMENT_ID = 31L;
    private static final long COURSE_ID = 5L;
    private static final LocalDateTime OLD_DEADLINE = LocalDateTime.now().minusDays(1).withNano(0);
    private static final LocalDateTime NEW_DEADLINE = LocalDateTime.now().plusDays(5).withNano(0);

    private final TeachingMapper mapper = mock(TeachingMapper.class);
    private final AuditService audit = mock(AuditService.class);
    private final TeachingService service = new TeachingService(mapper, audit);
    private final UserPrincipal teacher = new UserPrincipal(7, "T7", "Teacher 7", Role.TEACHER, "", true, false, 0);

    private Map<String, Object> detail(String status) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", ASSIGNMENT_ID);
        value.put("status", status);
        value.put("standardGroupSize", 4);
        value.put("totalScore", new BigDecimal("100.00"));
        value.put("courseId", COURSE_ID);
        value.put("groupCount", 0);
        value.put("submissionCount", 0);
        value.put("deadline", OLD_DEADLINE);
        return value;
    }

    private Map<String, Object> part(String name, double weight) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", name);
        value.put("weight", BigDecimal.valueOf(weight));
        return value;
    }

    private AssignmentUpdateRequest request(double secondWeight, int groupSize, double totalScore,
                                            LocalDateTime deadline, String secondName) {
        return new AssignmentUpdateRequest("需求分析", "说明", deadline, groupSize, totalScore,
                List.of(new AssignmentCreateRequest.BranchRequest("作业质量", "", 1.0 - secondWeight),
                        new AssignmentCreateRequest.BranchRequest(secondName, "", secondWeight)));
    }

    private AssignmentUpdateRequest unchangedRequest() {
        return request(0.4, 4, 100, NEW_DEADLINE, "团队协作");
    }

    @BeforeEach
    void defaults() {
        when(mapper.ownsAssignment(ASSIGNMENT_ID, 7)).thenReturn(1);
        when(mapper.teacherAssignmentDetail(ASSIGNMENT_ID, 7)).thenReturn(detail("PUBLISHED"));
        when(mapper.assignmentDependencyCount(ASSIGNMENT_ID)).thenReturn(0);
        when(mapper.assignmentPublishedScoreCount(ASSIGNMENT_ID)).thenReturn(0);
        when(mapper.assignmentDeadlineForTeacher(ASSIGNMENT_ID, 7)).thenReturn(OLD_DEADLINE);
        when(mapper.assignmentParts(ASSIGNMENT_ID))
                .thenReturn(List.of(part("作业质量", 0.6), part("团队协作", 0.4)));
        when(mapper.updateAssignment(eq(ASSIGNMENT_ID), eq(7L), any())).thenReturn(1);
        when(mapper.archiveAssignment(ASSIGNMENT_ID, 7)).thenReturn(1);
    }

    @Test
    void rejectsAssignmentOwnedByAnotherTeacher() {
        when(mapper.ownsAssignment(ASSIGNMENT_ID, 7)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAssignment(teacher, ASSIGNMENT_ID, unchangedRequest()));
        assertEquals(HttpStatus.NOT_FOUND, exception.status());
        verify(mapper, never()).updateAssignment(eq(ASSIGNMENT_ID), eq(7L), any());
    }

    @Test
    void rejectsWeightSumOtherThanOne() {
        var request = new AssignmentUpdateRequest("需求分析", "", NEW_DEADLINE, 4, 100,
                List.of(new AssignmentCreateRequest.BranchRequest("A", "", 0.6),
                        new AssignmentCreateRequest.BranchRequest("B", "", 0.3)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAssignment(teacher, ASSIGNMENT_ID, request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
    }

    @Test
    void rejectsDuplicateBranchNames() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAssignment(teacher, ASSIGNMENT_ID,
                        request(0.4, 4, 100, NEW_DEADLINE, " 作业质量 ")));
        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
    }

    @Test
    void rewritesBranchesAndWritesAuditWhenNoDownstreamDataExists() {
        service.updateAssignment(teacher, ASSIGNMENT_ID, unchangedRequest());

        verify(mapper).updateAssignment(eq(ASSIGNMENT_ID), eq(7L), any());
        verify(mapper).deleteAssignmentParts(ASSIGNMENT_ID);
        verify(mapper).insertAssignmentPart(ASSIGNMENT_ID, "作业质量", "", 0.6, 1);
        verify(mapper).insertAssignmentPart(ASSIGNMENT_ID, "团队协作", "", 0.4, 2);
        verify(audit).record(eq(teacher), eq("ASSIGNMENT_UPDATED"), eq("ASSIGNMENT"),
                eq(ASSIGNMENT_ID), anyMap());
    }

    @Test
    void locksBranchesWhenTeamsWorkloadsSubmissionsOrScoringExist() {
        when(mapper.assignmentDependencyCount(ASSIGNMENT_ID)).thenReturn(2);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAssignment(teacher, ASSIGNMENT_ID,
                        request(0.5, 4, 100, NEW_DEADLINE, "团队协作")));
        assertEquals(HttpStatus.CONFLICT, exception.status());
        verify(mapper, never()).deleteAssignmentParts(ASSIGNMENT_ID);
    }

    @Test
    void locksStandardGroupSizeWhenDownstreamDataExists() {
        when(mapper.assignmentDependencyCount(ASSIGNMENT_ID)).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAssignment(teacher, ASSIGNMENT_ID,
                        request(0.4, 3, 100, NEW_DEADLINE, "团队协作")));
        assertEquals(HttpStatus.CONFLICT, exception.status());
    }

    @Test
    void locksTotalScoreAfterScoresArePublished() {
        when(mapper.assignmentPublishedScoreCount(ASSIGNMENT_ID)).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAssignment(teacher, ASSIGNMENT_ID,
                        request(0.4, 4, 90, NEW_DEADLINE, "团队协作")));
        assertEquals(HttpStatus.CONFLICT, exception.status());
    }

    @Test
    void keepsExistingExpiredDeadlineWhenItIsNotChanged() {
        var request = request(0.4, 4, 100, OLD_DEADLINE, "团队协作");

        service.updateAssignment(teacher, ASSIGNMENT_ID, request);

        verify(mapper).updateAssignment(eq(ASSIGNMENT_ID), eq(7L), eq(request));
    }

    @Test
    void rejectsMovingDeadlineIntoThePast() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAssignment(teacher, ASSIGNMENT_ID,
                        request(0.4, 4, 100, LocalDateTime.now().minusDays(2), "团队协作")));
        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
    }

    @Test
    void detailExposesPartsAndLockFlags() {
        when(mapper.assignmentDependencyCount(ASSIGNMENT_ID)).thenReturn(3);
        when(mapper.assignmentPublishedScoreCount(ASSIGNMENT_ID)).thenReturn(1);

        Map<String, Object> result = service.teacherAssignmentDetail(teacher, ASSIGNMENT_ID);

        assertTrue((Boolean) result.get("partsLocked"));
        assertTrue((Boolean) result.get("scorePublished"));
        assertEquals(2, ((List<?>) result.get("parts")).size());
    }

    @Test
    void archivesInsteadOfPhysicallyDeletingAndWritesAudit() {
        service.archiveAssignment(teacher, ASSIGNMENT_ID);

        verify(mapper).archiveAssignment(ASSIGNMENT_ID, 7);
        verify(audit).record(eq(teacher), eq("ASSIGNMENT_ARCHIVED"), eq("ASSIGNMENT"),
                eq(ASSIGNMENT_ID), anyMap());
    }

    @Test
    void rejectsEditingAnArchivedAssignment() {
        when(mapper.teacherAssignmentDetail(ASSIGNMENT_ID, 7)).thenReturn(detail("ARCHIVED"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updateAssignment(teacher, ASSIGNMENT_ID, unchangedRequest()));
        assertEquals(HttpStatus.CONFLICT, exception.status());
    }

    @Test
    void rejectsArchivingTwice() {
        when(mapper.teacherAssignmentDetail(ASSIGNMENT_ID, 7)).thenReturn(detail("ARCHIVED"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.archiveAssignment(teacher, ASSIGNMENT_ID));
        assertEquals(HttpStatus.CONFLICT, exception.status());
        verify(mapper, never()).archiveAssignment(ASSIGNMENT_ID, 7);
    }

    @Test
    void teacherAssignmentListExcludesArchivedRows() throws Exception {
        String sql = String.join(" ", TeachingMapper.class
                .getMethod("teacherAssignments", long.class, long.class)
                .getAnnotation(org.apache.ibatis.annotations.Select.class).value());
        assertTrue(sql.contains("a.status<>'ARCHIVED'"));
        assertFalse(sql.contains("DELETE"));
    }
}
