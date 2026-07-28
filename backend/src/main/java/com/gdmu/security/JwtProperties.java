package com.gdmu.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secretBase64, Duration accessTtl, Duration refreshTtl, String issuer) {
}
