package com.gdmu.scoring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.model")
public record ModelProperties(String baseUrl, String token, Duration connectTimeout, Duration readTimeout) {
}
