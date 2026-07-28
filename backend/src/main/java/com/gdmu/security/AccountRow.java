package com.gdmu.security;

public record AccountRow(long id, String username, String passwordHash, String displayName,
                         String role, int status, boolean mustChangePassword, int tokenVersion) {
    public UserPrincipal toPrincipal() {
        return new UserPrincipal(id, username, displayName, Role.parse(role), passwordHash,
                status == 1, mustChangePassword, tokenVersion);
    }
}
