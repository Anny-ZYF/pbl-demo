package com.gdmu.security;

import java.time.LocalDateTime;

public record RefreshTokenRow(long id, long userId, String username, String role,
                              String displayName, int status, boolean mustChangePassword,
                              int tokenVersion,
                              LocalDateTime expiresAt, LocalDateTime revokedAt) {
}
