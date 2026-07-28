package com.gdmu.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsAnAccessTokenAfterTheAccountTokenVersionChanges() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        AccountMapper mapper = mock(AccountMapper.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, mapper);
        UserPrincipal tokenUser = new UserPrincipal(5, "S5", "学生5", Role.STUDENT,
                "", true, false, 3);
        when(jwtService.parseAccessToken("old-token")).thenReturn(tokenUser);
        when(mapper.findByUsernameAndRole("S5", "STUDENT")).thenReturn(Optional.of(
                new AccountRow(5, "S5", "hash", "学生5", "STUDENT", 1, false, 4)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/student/courses");
        request.addHeader("Authorization", "Bearer old-token");
        AtomicBoolean authenticatedInsideChain = new AtomicBoolean(true);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            authenticatedInsideChain.set(SecurityContextHolder.getContext().getAuthentication() != null);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(authenticatedInsideChain).isFalse();
    }

    @Test
    void acceptsAnEnabledAccountWithTheSameTokenVersion() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        AccountMapper mapper = mock(AccountMapper.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, mapper);
        UserPrincipal tokenUser = new UserPrincipal(5, "S5", "学生5", Role.STUDENT,
                "", true, false, 4);
        when(jwtService.parseAccessToken("current-token")).thenReturn(tokenUser);
        when(mapper.findByUsernameAndRole("S5", "STUDENT")).thenReturn(Optional.of(
                new AccountRow(5, "S5", "hash", "学生5", "STUDENT", 1, false, 4)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/student/courses");
        request.addHeader("Authorization", "Bearer current-token");
        AtomicBoolean authenticatedInsideChain = new AtomicBoolean(false);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            authenticatedInsideChain.set(SecurityContextHolder.getContext().getAuthentication() != null);
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(authenticatedInsideChain).isTrue();
    }
}
