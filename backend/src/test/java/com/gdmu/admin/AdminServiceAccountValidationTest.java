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
    void newClassNameIsCreatedOnDemand() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        when(mapper.classIdByName(1, "24莞信管31班")).thenReturn(null, 77L);
        var request = new AccountCreateRequest("STUDENT", "S031", "学生", "Student123", 1L, 2L, null, 2026,
                "24莞信管31班");

        service.createAccount(admin, request);

        verify(mapper).upsertClass(1L, "24莞信管31班");
        verify(mapper).insertStudent("S031", "encoded-password", "学生", 2L, 77L, 2026);
        verify(mapper, never()).activeClassInDepartment(anyLong(), anyLong());
    }

    @Test
    void classNameIsTrimmedBeforeLookup() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        when(mapper.classIdByName(1, "24莞信管01班")).thenReturn(9L);
        var request = new AccountCreateRequest("STUDENT", "S007", "学生", "Student123", 1L, 2L, null, 2026,
                "  24莞信管01班 ");

        service.createAccount(admin, request);

        verify(mapper, never()).upsertClass(anyLong(), anyString());
        verify(mapper).insertStudent("S007", "encoded-password", "学生", 2L, 9L, 2026);
    }

    @Test
    void classNameIsIndependentOfSelectedMajor() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(5, 1)).thenReturn(1);
        when(mapper.classIdByName(1, "24莞信管07班")).thenReturn(9L);
        var request = new AccountCreateRequest("STUDENT", "S008", "学生", "Student123", 1L, 5L, null, 2026,
                "24莞信管07班");

        service.createAccount(admin, request);

        verify(mapper).activeMajorInDepartment(5L, 1L);
        verify(mapper).insertStudent("S008", "encoded-password", "学生", 5L, 9L, 2026);
    }

    @Test
    void teacherCannotCarryClassName() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        var request = new AccountCreateRequest("TEACHER", "T002", "教师", "Teacher123", 1L, null, null, null, "7");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("教师账号不能设置专业、班级或年级", exception.getMessage());
        verify(mapper, never()).insertTeacher(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void studentRequiresEitherClassIdOrClassName() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        var request = new AccountCreateRequest("STUDENT", "S009", "学生", "Student123", 1L, 2L, null, 2026, "   ");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("学生班级不能为空", exception.getMessage());
    }

    @Test
    void classNameLongerThanColumnIsRejected() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        var request = new AccountCreateRequest("STUDENT", "S010", "学生", "Student123", 1L, 2L, null, 2026,
                "班".repeat(101));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("班级名称不能超过100个字符", exception.getMessage());
        verify(mapper, never()).upsertClass(anyLong(), anyString());
    }

    @Test
    void httpRequestBodyDeserializesClassName() throws Exception {
        String json = """
                {"role":"STUDENT","username":"S011","displayName":"学生","departmentId":1,
                 "majorId":2,"className":"24莞信管01班","grade":2026}
                """;

        AccountCreateRequest request = new ObjectMapper().readValue(json, AccountCreateRequest.class);

        assertEquals("24莞信管01班", request.className());
        assertEquals(2L, request.majorId());
        assertNull(request.classId());
    }

    @Test
    void legacyClassNumberAliasStillDeserializes() throws Exception {
        String json = """
                {"role":"STUDENT","username":"S012","displayName":"学生","departmentId":1,
                 "majorId":2,"classNumber":31,"grade":2026}
                """;

        AccountCreateRequest request = new ObjectMapper().readValue(json, AccountCreateRequest.class);

        assertEquals("31", request.className());
    }

    @Test
    void updateAccountAlsoResolvesClassName() {
        when(mapper.activeDepartment(1)).thenReturn(1);
        when(mapper.activeMajorInDepartment(2, 1)).thenReturn(1);
        when(mapper.classIdByName(1, "24莞数科45班")).thenReturn(null, 88L);
        when(mapper.updateStudent(anyLong(), anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        var request = new AccountUpdateRequest("STUDENT", "学生", 1L, 2L, null, 2026, "24莞数科45班");

        service.updateAccount(admin, 12L, request);

        verify(mapper).upsertClass(1L, "24莞数科45班");
        verify(mapper).updateStudent(12L, "学生", 2L, 88L, 2026);
    }

    @Test
    void newDepartmentNameIsCreatedOnDemand() {
        when(mapper.departmentIdByName("健康医学院")).thenReturn(null, 55L);
        var request = new AccountCreateRequest("TEACHER", "T021", "教师", "Teacher123", null, null, null, null,
                null, "健康医学院", null);

        service.createAccount(admin, request);

        verify(mapper).upsertDepartment("健康医学院");
        verify(mapper, never()).activeDepartment(anyLong());
        verify(mapper).insertTeacher("T021", "encoded-password", "教师", 55L);
    }

    @Test
    void newMajorNameIsCreatedOnDemand() {
        when(mapper.departmentIdByName("生物医学工程学院")).thenReturn(3L);
        when(mapper.majorIdByName(3L, "智能医学工程")).thenReturn(null, 66L);
        when(mapper.classIdByName(3L, "24级智医01班")).thenReturn(null, 77L);
        var request = new AccountCreateRequest("STUDENT", "S021", "学生", "Student123", null, null, null, 2026,
                "24级智医01班", "生物医学工程学院", "智能医学工程");

        service.createAccount(admin, request);

        verify(mapper).upsertMajor(3L, "智能医学工程");
        verify(mapper).upsertClass(3L, "24级智医01班");
        verify(mapper).insertStudent("S021", "encoded-password", "学生", 66L, 77L, 2026);
    }

    @Test
    void organizationNameWinsOverId() {
        when(mapper.departmentIdByName("信息管理学院")).thenReturn(8L);
        when(mapper.majorIdByName(8L, "信息管理与信息系统")).thenReturn(9L);
        when(mapper.classIdByName(8L, "24莞信管01班")).thenReturn(10L);
        var request = new AccountCreateRequest("STUDENT", "S022", "学生", "Student123", 1L, 2L, 3L, 2026,
                "24莞信管01班", "信息管理学院", "信息管理与信息系统");

        service.createAccount(admin, request);

        verify(mapper, never()).activeDepartment(anyLong());
        verify(mapper, never()).activeMajorInDepartment(anyLong(), anyLong());
        verify(mapper, never()).activeClassInDepartment(anyLong(), anyLong());
        verify(mapper).insertStudent("S022", "encoded-password", "学生", 9L, 10L, 2026);
    }

    @Test
    void majorNameLongerThanColumnIsRejected() {
        when(mapper.departmentIdByName("信息管理学院")).thenReturn(8L);
        var request = new AccountCreateRequest("STUDENT", "S023", "学生", "Student123", null, null, null, 2026,
                null, "信息管理学院", "专".repeat(101));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createAccount(admin, request));

        assertEquals("专业名称不能超过100个字符", exception.getMessage());
        verify(mapper, never()).upsertMajor(anyLong(), anyString());
    }
}
