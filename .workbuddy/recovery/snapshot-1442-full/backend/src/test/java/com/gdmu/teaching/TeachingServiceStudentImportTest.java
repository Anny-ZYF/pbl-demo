package com.gdmu.teaching;

import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeachingServiceStudentImportTest {
    private static final long COURSE_ID = 301L;
    private static final long TEACHER_ID = 7L;

    private TeachingMapper mapper;
    private AuditService audit;
    private TeachingService service;

    @BeforeEach
    void setUp() {
        mapper = mock(TeachingMapper.class);
        audit = mock(AuditService.class);
        service = new TeachingService(mapper, audit);
        when(mapper.ownsCourse(COURSE_ID, TEACHER_ID)).thenReturn(1);
    }

    private UserPrincipal teacher() {
        return new UserPrincipal(TEACHER_ID, "T7", "教师7", Role.TEACHER, "", true, false, 0);
    }

    private StudentCourseUpdateResult importing(StudentImportRequest request) {
        return service.importStudents(teacher(), COURSE_ID, request);
    }

    @Test
    void importsAllActiveStudentsFromSeveralClassesWithoutDuplicates() {
        when(mapper.activeClass(1L)).thenReturn(1);
        when(mapper.activeClass(2L)).thenReturn(1);
        when(mapper.activeStudentIdsByClass(1L)).thenReturn(List.of(11L, 12L));
        when(mapper.activeStudentIdsByClass(2L)).thenReturn(List.of(12L, 13L));

        StudentCourseUpdateResult result = importing(new StudentImportRequest(List.of(1L, 2L), null));

        assertThat(result.success()).isEqualTo(3);
        assertThat(result.skipped()).isZero();
        assertThat(result.notFound()).isEmpty();
        verify(mapper, times(1)).importStudent(11L, COURSE_ID);
        verify(mapper, times(1)).importStudent(12L, COURSE_ID);
        verify(mapper, times(1)).importStudent(13L, COURSE_ID);
    }

    @Test
    void skipsActiveMembersAndReactivatesDroppedOnes() {
        when(mapper.activeClass(1L)).thenReturn(1);
        when(mapper.activeStudentIdsByClass(1L)).thenReturn(List.of(11L, 12L));
        when(mapper.studentCourseStatus(11L, COURSE_ID)).thenReturn("ACTIVE");
        when(mapper.studentCourseStatus(12L, COURSE_ID)).thenReturn("DROPPED");

        StudentCourseUpdateResult result = importing(new StudentImportRequest(List.of(1L), null));

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(mapper, never()).importStudent(11L, COURSE_ID);
        verify(mapper).importStudent(12L, COURSE_ID);
    }

    @Test
    void mergesClassSelectionAndStudentNumbers() {
        when(mapper.activeClass(1L)).thenReturn(1);
        when(mapper.activeStudentIdsByClass(1L)).thenReturn(List.of(11L, 12L));
        when(mapper.studentIdByNo("S11")).thenReturn(11L);
        when(mapper.studentIdByNo("S99")).thenReturn(null);

        StudentCourseUpdateResult result = importing(
                new StudentImportRequest(List.of(1L), List.of("S11", "S99", "S11")));

        assertThat(result.success()).isEqualTo(2);
        assertThat(result.notFound()).containsExactly("S99");
        verify(mapper, times(1)).importStudent(11L, COURSE_ID);
    }

    @Test
    void legacyStudentNumbersOnlyRequestStillWorks() {
        when(mapper.studentIdByNo("S1")).thenReturn(21L);

        StudentCourseUpdateResult result = importing(new StudentImportRequest(List.of("S1")));

        assertThat(result.success()).isEqualTo(1);
        verify(mapper).importStudent(21L, COURSE_ID);
    }

    @Test
    void rejectsInactiveOrUnknownClass() {
        when(mapper.activeClass(404L)).thenReturn(0);

        assertThatThrownBy(() -> importing(new StudentImportRequest(List.of(404L), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级不存在或已停用");
        verify(mapper, never()).importStudent(anyLong(), eq(COURSE_ID));
    }

    @Test
    void rejectsAnEmptyImportRequest() {
        assertThatThrownBy(() -> importing(new StudentImportRequest(List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择要导入的班级或填写学号");
    }

    @Test
    void capsTheNumberOfStudentsInOneBatch() {
        when(mapper.activeClass(1L)).thenReturn(1);
        when(mapper.activeStudentIdsByClass(1L))
                .thenReturn(LongStream.rangeClosed(1, 2001).boxed().toList());

        assertThatThrownBy(() -> importing(new StudentImportRequest(List.of(1L), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2000");
        verify(mapper, never()).importStudent(anyLong(), eq(COURSE_ID));
    }

    @Test
    void rejectsCourseOwnedByAnotherTeacher() {
        when(mapper.ownsCourse(99L, TEACHER_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.importStudents(teacher(), 99L,
                new StudentImportRequest(List.of("S1"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前教师");
        assertThatThrownBy(() -> service.importableClasses(teacher(), 99L, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rosterRequiresAnActiveClass() {
        when(mapper.activeClass(5L)).thenReturn(0);

        assertThatThrownBy(() -> service.classRoster(teacher(), COURSE_ID, 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级不存在或已停用");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void auditRecordsSelectedClassIdsAndCounts() {
        when(mapper.activeClass(1L)).thenReturn(1);
        when(mapper.activeStudentIdsByClass(1L)).thenReturn(List.of(11L, 12L));
        when(mapper.studentCourseStatus(11L, COURSE_ID)).thenReturn("ACTIVE");
        when(mapper.studentIdByNo("S99")).thenReturn(null);

        importing(new StudentImportRequest(List.of(1L), List.of("S99")));

        ArgumentCaptor<Map> detail = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(any(UserPrincipal.class), eq("STUDENTS_IMPORTED"), eq("COURSE"),
                eq(COURSE_ID), detail.capture());
        assertThat(detail.getValue())
                .containsEntry("success", 1)
                .containsEntry("skipped", 1)
                .containsEntry("notFound", 1)
                .containsEntry("classIds", "[1]")
                .containsEntry("studentNos", 1);
    }
}
