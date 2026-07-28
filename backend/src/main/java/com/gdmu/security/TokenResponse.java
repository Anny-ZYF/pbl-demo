package com.gdmu.security;

public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                            long expiresIn, String role, boolean mustChangePassword) {
}
