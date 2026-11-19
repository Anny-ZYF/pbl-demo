package com.gdmu.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.AccountMapper;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Account deletion is a tombstone-based logical delete; organization deletion is guarded. */
class AdminServiceDeletionTest {
    private final AdminMapper mapper = mock(AdminMapper.class);
    private final AccountMapper accountMapper = mock(AccountMapper.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminService service = new AdminService(
            mapper, accountMapper, passwordEncoder, auditService, new ObjectMapper());
    private final UserPrincipal admin = new UserPrincipal(1, "admin", "Admin", Role.ADMIN, "", true, false, 0);

    private Map<String, Object> usage(Object... keysAndValues) {
        Map<String, Object> usage = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            usage.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return usage;
    }

    @Test
    void adminAccountsAreOutOfScope() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteAccount(admin, 1, "ADMIN"));

        assertEquals("只能注销教师或学生账号", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        verify(mapper, never()).softDeleteTeacher(anyLong(), anyString(), anyLong());
        verify(mapper, never()).softDeleteStudent(anyLong(), anyString(), anyLong());
    }

    @Test
    void missingOrAlreadyDeletedAccountIsRejected() {
        when(mapper.accountForDeletion(9L, "STUDENT")).thenReturn(null);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.deleteAccount(admin, 9, "STUDENT"));
        assertEquals(HttpStatus.NOT_FOUND, missing.status());

        when(mapper.accountForDeletion(9L, "STUDENT"))
                .thenReturn(Map.of("username", "S009", "deletedAt", "2026-11-19 10:00:00"));
        BusinessException deleted = assertThrows(BusinessException.class,
                () -> service.deleteAccount(admin, 9, "STUDENT"));
        assertEquals("账号不存在或已注销", deleted.getMessage());
        verify(mapper, never()).softDeleteStudent(anyLong(), anyString(), anyLong());
    }

    @Test
    void teacherDeletionKeepsTheRowButRevokesEverySession() {
        when(mapper.accountForDeletion(7L, "TEACHER")).thenReturn(Map.of("username", "T007"));
        when(mapper.accountUsernameCount("#7")).thenReturn(0);
        when(mapper.softDeleteTeacher(7, "#7", 1)).thenReturn(1);

        Map<String, Object> result = service.deleteAccount(admin, 7, "ROLE_TEACHER");

        assertEquals("LOGICAL_DELETE", result.get("mode"));
        assertEquals("T007", result.get("username"));
        verify(mapper).softDeleteTeacher(7, "#7", 1);
        verify(accountMapper).revokeAllRefreshTokens(7, "TEACHER");
        verify(auditService).record(eq(admin), eq("ACCOUNT_DELETED"), eq("ACCOUNT"), eq("TEACHER:7"), any());
    }

    @Test
    void studentDeletionAlsoFreesTheOriginalUsername() {
        when(mapper.accountForDeletion(66L, "STUDENT")).thenReturn(Map.of("username", "demo_student_04"));
        when(mapper.accountUsernameCount("#66")).thenReturn(0);
        when(mapper.softDeleteStudent(66, "#66", 1)).thenReturn(1);

        Map<String, Object> result = service.deleteAccount(admin, 66, "STUDENT");

        assertEquals("demo_student_04", result.get("username"));
        verify(mapper).softDeleteStudent(66, "#66", 1);
        verify(accountMapper).revokeAllRefreshTokens(66, "STUDENT");
        // The original username is no longer occupied in the unified account view.
        verify(mapper).accountUsernameCount("#66");
    }

    @Test
    void tombstoneCollisionIsReportedAsAConflict() {
        when(mapper.accountForDeletion(7L, "TEACHER")).thenReturn(Map.of("username", "T007"));
        when(mapper.accountUsernameCount("#7")).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteAccount(admin, 7, "TEACHER"));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        verify(mapper, never()).softDeleteTeacher(anyLong(), anyString(), anyLong());
        verify(accountMapper, never()).revokeAllRefreshTokens(anyLong(), anyString());
    }

    @Test
    void usernamesStartingWithHashAreReservedForTombstones() {
        var request = new AccountCreateRequest("TEACHER", "#7", "教师", "Teacher123", 1L, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createAccount(admin, request));

        assertEquals("登录账号不能以 # 开头", exception.getMessage());
        verify(mapper, never()).insertTeacher(anyString(), anyString(), anyString(), any());
    }

    @Test
    void emptyClassCanBeDeleted() {
        when(mapper.classById(6)).thenReturn(Map.of("id", 6, "name", "2"));
        when(mapper.classUsage(6)).thenReturn(usage("students", 0L, "groups", 0L));
        when(mapper.deleteClass(6)).thenReturn(1);

        Map<String, Object> result = service.deleteOrganization(admin, "class", 6);

        assertEquals("CLASS", result.get("type"));
        assertEquals("2", result.get("name"));
        verify(mapper).deleteClass(6);
        verify(auditService).record(eq(admin), eq("ORGANIZATION_DELETED"), eq("ORGANIZATION"),
                eq("CLASS:6"), any());
    }

    @Test
    void classWithStudentsOrTeamsIsProtected() {
        when(mapper.classById(76)).thenReturn(Map.of("id", 76, "name", "16"));
        when(mapper.classUsage(76)).thenReturn(usage("students", 7L, "groups", 2L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteOrganization(admin, "CLASS", 76));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("该班级仍有引用，不能删除：7名在册学生、2个关联小组", exception.getMessage());
        verify(mapper, never()).deleteClass(anyLong());
    }

    @Test
    void majorWithCourseOrChildReferencesIsProtected() {
        when(mapper.majorById(3)).thenReturn(Map.of("id", 3, "name", "信息管理与信息系统"));
        when(mapper.majorUsage(3)).thenReturn(usage("students", 47L, "classes", 0L,
                "courses", 1L, "courseMajors", 0L, "groups", 0L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteOrganization(admin, "major", 3));

        assertEquals("该专业仍有引用，不能删除：47名在册学生、1门关联课程", exception.getMessage());
        verify(mapper, never()).deleteMajor(anyLong());
    }

    @Test
    void departmentWithChildrenIsProtected() {
        when(mapper.departmentById(1)).thenReturn(Map.of("id", 1, "name", "生物医学工程学院"));
        when(mapper.departmentUsage(1)).thenReturn(usage("teachers", 1L, "majors", 5L, "classes", 36L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteOrganization(admin, "department", 1));

        assertEquals("该院系仍有引用，不能删除：1名在职教师、5个下属专业、36个下属班级", exception.getMessage());
        verify(mapper, never()).deleteDepartment(anyLong());
    }

    @Test
    void organizationOverviewCarriesLiveMemberCountsForEmptyNodeDetection() {
        when(mapper.organizationOverview()).thenReturn(Map.of("activeTeacherCount", 1, "activeStudentCount", 116));
        when(mapper.departments()).thenReturn(List.of(new LinkedHashMap<>(Map.of("id", 1L, "name", "生物医学工程学院"))));
        when(mapper.majors()).thenReturn(List.of(new LinkedHashMap<>(Map.of(
                "id", 3L, "departmentId", 1L, "name", "信息管理与信息系统"))));
        when(mapper.classes()).thenReturn(List.of(new LinkedHashMap<>(Map.of(
                "id", 6L, "departmentId", 1L, "name", "2"))));
        when(mapper.teacherCountsByDepartment()).thenReturn(List.of(Map.of("departmentId", 1L, "memberCount", 1L)));
        when(mapper.studentCountsByDepartment()).thenReturn(List.of(Map.of("departmentId", 1L, "memberCount", 116L)));
        when(mapper.studentCountsByMajor()).thenReturn(List.of(Map.of("majorId", 3L, "memberCount", 47L)));
        when(mapper.studentCountsByClass()).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> departments = (List<Map<String, Object>>) service.organizationOverview().get("departments");
        Map<String, Object> department = departments.getFirst();
        assertEquals(117L, department.get("memberCount"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> majors = (List<Map<String, Object>>) department.get("majors");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> classes = (List<Map<String, Object>>) department.get("classes");
        assertEquals(47L, majors.getFirst().get("memberCount"));
        assertEquals(0L, classes.getFirst().get("memberCount"));
    }

    @Test
    void unknownOrganizationTypeOrMissingNodeIsRejected() {
        BusinessException badType = assertThrows(BusinessException.class,
                () -> service.deleteOrganization(admin, "campus", 1));
        assertEquals("组织类型必须是 department、major 或 class", badType.getMessage());

        when(mapper.classById(999)).thenReturn(null);
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.deleteOrganization(admin, "class", 999));
        assertEquals(HttpStatus.NOT_FOUND, missing.status());
    }
}
