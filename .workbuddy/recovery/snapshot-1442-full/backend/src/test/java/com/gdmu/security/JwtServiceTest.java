package com.gdmu.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private final JwtService service = new JwtService(new JwtProperties(
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()),
            Duration.ofMinutes(30), Duration.ofDays(7), "groupwork-test"));

    @Test
    void preservesTheMandatoryPasswordChangeFlagInTheSignedToken() {
        UserPrincipal source = new UserPrincipal(12, "S2025001", "测试学生", Role.STUDENT,
                "hash", true, true, 7);

        UserPrincipal parsed = service.parseAccessToken(service.createAccessToken(source));

        assertThat(parsed.id()).isEqualTo(12);
        assertThat(parsed.role()).isEqualTo(Role.STUDENT);
        assertThat(parsed.mustChangePassword()).isTrue();
        assertThat(parsed.tokenVersion()).isEqualTo(7);
    }
}
