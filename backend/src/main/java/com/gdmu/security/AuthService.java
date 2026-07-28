package com.gdmu.security;

import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Service
public class AuthService {
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AccountMapper accountMapper, PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuditService auditService) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String username = request.username().trim();
        AccountRow account;
        if (request.role() == null || request.role().isBlank()) {
            var candidates = accountMapper.findAllByUsername(username);
            if (candidates.size() != 1) {
                throw badCredentials();
            }
            account = candidates.getFirst();
        } else {
            Role requestedRole = Role.parse(request.role());
            account = accountMapper.findByUsernameAndRole(username, requestedRole.name())
                    .orElseThrow(this::badCredentials);
        }
        if (account.status() != 1 || !passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw badCredentials();
        }
        UserPrincipal principal = account.toPrincipal();
        if (principal.role() == Role.ADMIN) {
            accountMapper.touchAdminLogin(account.id());
        }
        TokenResponse tokens = issuePair(principal);
        auditService.record(principal, "LOGIN_SUCCESS", "ACCOUNT", principal.id(),
                Map.of("mustChangePassword", principal.mustChangePassword()));
        return tokens;
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        RefreshTokenRow stored = accountMapper.findRefreshToken(hash).orElseThrow(this::invalidRefreshToken);
        if (stored.revokedAt() != null || stored.expiresAt().isBefore(LocalDateTime.now()) || stored.status() != 1) {
            throw invalidRefreshToken();
        }
        if (accountMapper.revokeRefreshToken(stored.id()) != 1) {
            throw invalidRefreshToken();
        }
        UserPrincipal user = new UserPrincipal(stored.userId(), stored.username(), stored.displayName(),
                Role.parse(stored.role()), "", true, stored.mustChangePassword(), stored.tokenVersion());
        return issuePair(user);
    }

    @Transactional
    public void logout(UserPrincipal user) {
        accountMapper.revokeAllRefreshTokens(user.id(), user.role().name());
        if (bumpTokenVersion(user) != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "账号会话状态更新失败");
        }
        auditService.record(user, "LOGOUT", "ACCOUNT", user.id(), Map.of());
    }

    @Transactional
    public void changePassword(UserPrincipal user, ChangePasswordRequest request) {
        AccountRow current = accountMapper.findByUsernameAndRole(user.username(), user.role().name())
                .orElseThrow(this::badCredentials);
        if (!passwordEncoder.matches(request.currentPassword(), current.passwordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), current.passwordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        String hash = passwordEncoder.encode(request.newPassword());
        int changed = switch (user.role()) {
            case ADMIN -> accountMapper.updateAdminPassword(user.id(), hash);
            case TEACHER -> accountMapper.updateTeacherPassword(user.id(), hash);
            case STUDENT -> accountMapper.updateStudentPassword(user.id(), hash);
        };
        if (changed != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "密码更新失败");
        }
        accountMapper.revokeAllRefreshTokens(user.id(), user.role().name());
        auditService.record(user, "PASSWORD_CHANGED", "ACCOUNT", user.id(), Map.of());
    }

    public MeResponse me(UserPrincipal user) {
        AccountRow current = accountMapper.findByUsernameAndRole(user.username(), user.role().name())
                .orElseThrow(this::badCredentials);
        return new MeResponse(current.id(), current.username(), current.displayName(), current.role(),
                current.mustChangePassword());
    }

    private TokenResponse issuePair(UserPrincipal user) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        accountMapper.insertRefreshToken(user.id(), user.username(), user.role().name(), sha256(refreshToken),
                user.tokenVersion(), LocalDateTime.now().plusSeconds(jwtService.refreshTtlSeconds()));
        return new TokenResponse(jwtService.createAccessToken(user), refreshToken, "Bearer",
                jwtService.accessTtlSeconds(), user.role().name(), user.mustChangePassword());
    }

    private String sha256(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalidRefreshToken();
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private BusinessException badCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "用户名、密码或角色不正确");
    }

    private BusinessException invalidRefreshToken() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "刷新令牌无效或已过期");
    }

    private int bumpTokenVersion(UserPrincipal user) {
        return switch (user.role()) {
            case ADMIN -> accountMapper.bumpAdminTokenVersion(user.id());
            case TEACHER -> accountMapper.bumpTeacherTokenVersion(user.id());
            case STUDENT -> accountMapper.bumpStudentTokenVersion(user.id());
        };
    }
}
