package com.gdmu.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.gdmu.security.JwtService;
import com.gdmu.security.AccountMapper;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(AdminControllerSecurityTest.TestSecurity.class)
class AdminControllerSecurityTest {
    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurity {
        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, ex) -> response.setStatus(401)))
                    .build();
        }
    }

    @jakarta.annotation.Resource
    MockMvc mvc;

    @MockitoBean
    AdminService service;

    @MockitoBean
    AccountImportService accountImportService;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    AccountMapper accountMapper;

    @Test
    void anonymousUserCannotReadAdminDashboard() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotReadAdminDashboard() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadDashboard() throws Exception {
        when(service.dashboard()).thenReturn(Map.of("studentCount", 75));
        mvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.studentCount").value(75));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void malformedPaginationIsReportedAsAClientError() throws Exception {
        mvc.perform(get("/api/v1/admin/accounts").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotDeleteAccountsOrOrganizations() throws Exception {
        mvc.perform(delete("/api/v1/admin/accounts/66").param("role", "STUDENT"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/admin/organization/class/6"))
                .andExpect(status().isForbidden());
        verify(service, never()).deleteAccount(any(), anyLong(), anyString());
        verify(service, never()).deleteOrganization(any(), anyString(), anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCallBothDeletionEndpoints() throws Exception {
        when(service.deleteAccount(any(), anyLong(), anyString()))
                .thenReturn(Map.of("id", 66, "mode", "LOGICAL_DELETE"));
        when(service.deleteOrganization(any(), anyString(), anyLong()))
                .thenReturn(Map.of("id", 6, "type", "CLASS"));

        mvc.perform(delete("/api/v1/admin/accounts/66").param("role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("LOGICAL_DELETE"));
        mvc.perform(delete("/api/v1/admin/organization/class/6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("CLASS"));
    }
}
