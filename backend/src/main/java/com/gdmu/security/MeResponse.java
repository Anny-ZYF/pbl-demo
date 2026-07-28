package com.gdmu.security;

public record MeResponse(long id, String username, String displayName, String role, boolean mustChangePassword) {
}
