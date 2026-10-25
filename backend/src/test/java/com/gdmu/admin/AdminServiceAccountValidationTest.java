package com.gdmu.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.AccountMapper;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceAccountValidationTest {
    private final AdminMapper mapper = mock(AdminMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminService service = new AdminService(
            mapper, mock(AccountMapper.class), passwordEncoder, auditService, new ObjectMapper());
    private final UserPrincipal admin = new UserPrincipal(1, "admin", "Admin", Role.ADMIN, "", true, false, 0);

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
    }

    @Test
    void teacherRequiresActiveDepartment() {
        var request = new AccountCreateRequest("TEACHER", "T001", "教师", "Teacher123", null, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("教师所属学院不能为空", exception.getMessage());
        verify(mapper, never()).insertTeacher(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void studentMajorMustBelongToSelectedDepartment() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(0);
        var request = new AccountCreateRequest("STUDENT", "S001", "学生", "Student123", 1L, 2L, 3L, 2026);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("专业不属于所选学院或已停用", exception.getMessage());
        verify(mapper, never()).insertStudent(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void loginAccountMustBeUniqueAcrossRoles() {
        when(mapper.accountUsernameCount("shared001")).thenReturn(1);
        var request = new AccountCreateRequest("TEACHER", "shared001", "教师", "Teacher123", 1L, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("登录账号已被使用", exception.getMessage());
    }

    @Test
    void completeStudentProfileIsPersisted() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        when(mapper.activeClassInMajor(3, 2)).thenReturn(1);
        var request = new AccountCreateRequest("STUDENT", "S001", "学生", "Student123", 1L, 2L, 3L, 2026);

        service.createAccount(admin, request);

        verify(mapper).insertStudent("S001", "encoded-password", "学生", 2L, 3L, 2026);
    }

    @Test
    void blankInitialPasswordUsesAbcPlusLoginAccount() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        var request = new AccountCreateRequest("TEACHER", " T001 ", "教师", "", 1L, null, null, null);

        service.createAccount(admin, request);

        verify(passwordEncoder).encode("ABCT001");
        verify(mapper).insertTeacher("T001", "encoded-password", "教师", 1L);
    }
}
