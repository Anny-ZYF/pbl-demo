package com.gdmu.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountImportServiceTest {
    private final AdminService adminService = mock(AdminService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminMapper mapper = mock(AdminMapper.class);
    private final AccountImportService service = new AccountImportService(
            adminService, auditService, mapper, new ObjectMapper());
    private final UserPrincipal admin = new UserPrincipal(
            1, "admin", "管理员", Role.ADMIN, "", true, false, 0);

    @Test
    void studentImportResolvesAndCreatesOrganizationByName() {
        String csv = "role,username,displayName,department,major,class,grade,initialPassword\n"
                + "ROLE_STUDENT,2026001,测试学生,生物医学工程学院,智能科学与技术,1,2026,\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        when(mapper.departmentIdByName("生物医学工程学院")).thenReturn(10L);
        when(mapper.majorIdByName(10L, "智能科学与技术")).thenReturn(20L);
        when(mapper.classIdByName(10L, "1")).thenReturn(null, 30L);
        when(adminService.createAccount(any(UserPrincipal.class), any(AccountCreateRequest.class))).thenReturn(Map.of(
                "username", "2026001", "role", "ROLE_STUDENT", "temporaryPassword", "ABC2026001"));

        AccountImportService.ImportReport report = service.importCsv(admin, file);

        assertEquals(1, report.successRows());
        assertEquals(0, report.failureRows());
        verify(mapper).upsertDepartment("生物医学工程学院");
        verify(mapper).upsertMajor(10L, "智能科学与技术");
        verify(mapper).upsertClass(10L, "1");
        ArgumentCaptor<AccountCreateRequest> requestCaptor = ArgumentCaptor.forClass(AccountCreateRequest.class);
        verify(adminService).createAccount(org.mockito.ArgumentMatchers.eq(admin), requestCaptor.capture());
        AccountCreateRequest request = requestCaptor.getValue();
        assertEquals(10L, request.departmentId());
        assertEquals(20L, request.majorId());
        assertEquals(30L, request.classId());
        assertEquals(2026, request.grade());
    }

    @Test
    void chineseHeaderWithoutRoleColumnUsesSelectedRole() {
        String csv = "姓名,账号,学院,专业,班级,年级,初始密码\n"
                + "测试学生,2026002,生物医学工程学院,智能科学与技术,24莞信科01班,2026,\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        when(mapper.departmentIdByName("生物医学工程学院")).thenReturn(10L);
        when(mapper.majorIdByName(10L, "智能科学与技术")).thenReturn(20L);
        when(mapper.classIdByName(10L, "24莞信科01班")).thenReturn(30L);
        when(adminService.createAccount(any(UserPrincipal.class), any(AccountCreateRequest.class))).thenReturn(Map.of());

        AccountImportService.ImportReport report = service.importCsv(admin, file, "ROLE_STUDENT");

        assertEquals(1, report.successRows());
        assertEquals(0, report.failureRows());
        ArgumentCaptor<AccountCreateRequest> requestCaptor = ArgumentCaptor.forClass(AccountCreateRequest.class);
        verify(adminService).createAccount(org.mockito.ArgumentMatchers.eq(admin), requestCaptor.capture());
        AccountCreateRequest request = requestCaptor.getValue();
        assertEquals("ROLE_STUDENT", request.role());
        assertEquals("测试学生", request.displayName());
        assertEquals("2026002", request.username());
        assertEquals(10L, request.departmentId());
        assertEquals(20L, request.majorId());
        assertEquals(30L, request.classId());
    }

    @Test
    void teacherTemplateNeedsNoMajorOrClassColumns() {
        String csv = "姓名,账号,学院,初始密码\n"
                + "示例教师,T2026001,生物医学工程学院,\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "teachers.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        when(mapper.departmentIdByName("生物医学工程学院")).thenReturn(10L);
        when(adminService.createAccount(any(UserPrincipal.class), any(AccountCreateRequest.class))).thenReturn(Map.of());

        AccountImportService.ImportReport report = service.importCsv(admin, file, "ROLE_TEACHER");

        assertEquals(1, report.successRows());
        ArgumentCaptor<AccountCreateRequest> requestCaptor = ArgumentCaptor.forClass(AccountCreateRequest.class);
        verify(adminService).createAccount(org.mockito.ArgumentMatchers.eq(admin), requestCaptor.capture());
        AccountCreateRequest request = requestCaptor.getValue();
        assertEquals("ROLE_TEACHER", request.role());
        assertEquals(null, request.majorId());
        assertEquals(null, request.classId());
    }

    @Test
    void columnOrderDoesNotMatter() {
        String csv = "姓名,学院,账号\n"
                + "倒序教师,生物医学工程学院,T2026003\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        when(mapper.departmentIdByName("生物医学工程学院")).thenReturn(10L);
        when(adminService.createAccount(any(UserPrincipal.class), any(AccountCreateRequest.class))).thenReturn(Map.of());

        AccountImportService.ImportReport report = service.importCsv(admin, file, "教师");

        assertEquals(1, report.successRows());
        ArgumentCaptor<AccountCreateRequest> requestCaptor = ArgumentCaptor.forClass(AccountCreateRequest.class);
        verify(adminService).createAccount(org.mockito.ArgumentMatchers.eq(admin), requestCaptor.capture());
        assertEquals("倒序教师", requestCaptor.getValue().displayName());
        assertEquals("T2026003", requestCaptor.getValue().username());
    }
}
