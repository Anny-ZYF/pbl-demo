package com.gdmu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PasswordChangeRequiredFilterTest {
    private final PasswordChangeRequiredFilter filter = new PasswordChangeRequiredFilter(new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksBusinessApisUntilTheTemporaryPasswordIsChanged() throws Exception {
        authenticate(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/dashboard");
        request.setRequestURI("/api/v1/admin/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("必须先修改密码");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void permitsThePasswordChangeEndpoint() throws Exception {
        authenticate(true);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/v1/auth/change-password");
        request.setRequestURI("/api/v1/auth/change-password");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private void authenticate(boolean mustChangePassword) {
        UserPrincipal user = new UserPrincipal(3, "student", "学生", Role.STUDENT,
                "hash", true, mustChangePassword, 0);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
