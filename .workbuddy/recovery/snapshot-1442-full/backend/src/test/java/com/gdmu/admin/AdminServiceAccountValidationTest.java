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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
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
        when(mapper.activeClassInDepartment(3, 1)).thenReturn(1);
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

    @Test
    void newDepartmentScopedClassNumberIsCreatedOnDemand() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        when(mapper.classIdByName(1, "31")).thenReturn(null, 77L);
        var request = new AccountCreateRequest("STUDENT", "S031", "学生", "Student123", 1L, 2L, null, 2026, 31);

        service.createAccount(admin, request);

        verify(mapper).upsertClass(1L, "31");
        verify(mapper).insertStudent("S031", "encoded-password", "学生", 2L, 77L, 2026);
        verify(mapper, never()).activeClassInDepartment(anyLong(), anyLong());
    }

    @Test
    void existingClassNumberIsReusedWithoutCreatingAnotherClass() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        when(mapper.classIdByName(1, "7")).thenReturn(9L);
        var request = new AccountCreateRequest("STUDENT", "S007", "学生", "Student123", 1L, 2L, null, 2026, 7);

        service.createAccount(admin, request);

        verify(mapper, never()).upsertClass(anyLong(), anyString());
        verify(mapper).insertStudent("S007", "encoded-password", "学生", 2L, 9L, 2026);
    }

    @Test
    void classNumberIsIndependentOfSelectedMajor() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(5, 1)).thenReturn(1);
        when(mapper.classIdByName(1, "7")).thenReturn(9L);
        var request = new AccountCreateRequest("STUDENT", "S008", "学生", "Student123", 1L, 5L, null, 2026, 7);

        service.createAccount(admin, request);

        verify(mapper).activeMajorInDepartment(5L, 1L);
        verify(mapper).insertStudent("S008", "encoded-password", "学生", 5L, 9L, 2026);
    }

    @Test
    void teacherCannotCarryClassNumber() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        var request = new AccountCreateRequest("TEACHER", "T002", "教师", "Teacher123", 1L, null, null, null, 7);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("教师账号不能设置专业、班级或年级", exception.getMessage());
        verify(mapper, never()).insertTeacher(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void studentRequiresEitherClassIdOrClassNumber() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        var request = new AccountCreateRequest("STUDENT", "S009", "学生", "Student123", 1L, 2L, null, 2026, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("学生班级不能为空", exception.getMessage());
    }

    @Test
    void classNumberOutsideRangeIsRejected() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        var request = new AccountCreateRequest("STUDENT", "S010", "学生", "Student123", 1L, 2L, null, 2026, 10000);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("班级号必须是1至9999之间的整数", exception.getMessage());
        verify(mapper, never()).upsertClass(anyLong(), anyString());
    }

    @Test
    void httpRequestBodyDeserializesDepartmentScopedClassNumber() throws Exception {
        String json = """
                {"role":"STUDENT","username":"S011","displayName":"学生","departmentId":1,
                 "majorId":2,"classNumber":31,"grade":2026}
                """;

        AccountCreateRequest request = new ObjectMapper().readValue(json, AccountCreateRequest.class);

        assertEquals(31, request.classNumber());
        assertEquals(2L, request.majorId());
        assertNull(request.classId());
    }

    @Test
    void updateAccountAlsoResolvesDepartmentScopedClassNumber() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        when(mapper.classIdByName(1, "45")).thenReturn(null, 88L);
        when(mapper.updateStudent(anyLong(), anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        var request = new AccountUpdateRequest("STUDENT", "学生", 1L, 2L, null, 2026, 45);

        service.updateAccount(admin, 12L, request);

        verify(mapper).upsertClass(1L, "45");
        verify(mapper).updateStudent(12L, "学生", 2L, 88L, 2026);
    }
}
