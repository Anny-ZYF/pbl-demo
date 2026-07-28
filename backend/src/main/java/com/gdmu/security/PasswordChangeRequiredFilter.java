package com.gdmu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/change-password", "/api/v1/auth/logout");
    private final ObjectMapper objectMapper;

    public PasswordChangeRequiredFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!"OPTIONS".equals(request.getMethod())
                && authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal user
                && user.mustChangePassword()
                && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.error(403, "首次登录或密码重置后必须先修改密码"));
            return;
        }
        chain.doFilter(request, response);
    }
}
