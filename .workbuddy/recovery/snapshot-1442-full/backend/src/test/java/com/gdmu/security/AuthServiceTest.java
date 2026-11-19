package com.gdmu.security;

import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock AccountMapper mapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock AuditService auditService;
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(mapper, passwordEncoder, jwtService, auditService);
    }

    @Test
    void automaticallyUsesTheOnlyRoleForAUsername() {
        AccountRow account = new AccountRow(9, "T2025001", "$2a$hash", "王老师", "TEACHER", 1, false, 4);
        when(mapper.findAllByUsername("T2025001")).thenReturn(List.of(account));
        when(passwordEncoder.matches("Password9", "$2a$hash")).thenReturn(true);
        when(jwtService.createAccessToken(any())).thenReturn("access-token");
        when(jwtService.accessTtlSeconds()).thenReturn(1800L);
        when(jwtService.refreshTtlSeconds()).thenReturn(604800L);

        TokenResponse response = service.login(new LoginRequest("T2025001", "Password9", null));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.role()).isEqualTo("TEACHER");
        assertThat(response.refreshToken()).isNotBlank();
        verify(mapper).insertRefreshToken(eq(9L), eq("T2025001"), eq("TEACHER"),
                matches("[0-9a-f]{64}"), eq(4), any());
        verify(mapper, never()).findByUsernameAndRole(anyString(), anyString());
    }

    @Test
    void refusesAmbiguousCrossRoleLoginWithoutLeakingWhichRolesExist() {
        when(mapper.findAllByUsername("same-id")).thenReturn(List.of(
                new AccountRow(1, "same-id", "x", "A", "TEACHER", 1, false, 0),
                new AccountRow(2, "same-id", "y", "B", "STUDENT", 1, false, 0)));

        assertThatThrownBy(() -> service.login(new LoginRequest("same-id", "Password9", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名、密码或角色不正确");
        verifyNoInteractions(passwordEncoder, jwtService);
    }

    @Test
    void logoutRevokesRefreshTokensAndBumpsTheAccessTokenVersion() {
        UserPrincipal user = new UserPrincipal(11, "S11", "学生11", Role.STUDENT,
                "", true, false, 2);
        when(mapper.bumpStudentTokenVersion(11)).thenReturn(1);

        service.logout(user);

        verify(mapper).revokeAllRefreshTokens(11, "STUDENT");
        verify(mapper).bumpStudentTokenVersion(11);
    }
}
