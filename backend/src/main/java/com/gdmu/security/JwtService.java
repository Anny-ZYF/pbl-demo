package com.gdmu.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(properties.secretBase64());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("JWT_SECRET_BASE64 必须是合法 Base64", ex);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 解码后必须至少 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(decoded);
    }

    public String createAccessToken(UserPrincipal user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.username())
                .claim("uid", user.id())
                .claim("name", user.displayName())
                .claim("role", user.role().name())
                .claim("mustChangePassword", user.mustChangePassword())
                .claim("tokenVersion", user.tokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parseAccessToken(String token) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer()).build()
                .parseSignedClaims(token).getPayload();
        Number uid = claims.get("uid", Number.class);
        Role role = Role.parse(claims.get("role", String.class));
        Boolean mustChangePassword = claims.get("mustChangePassword", Boolean.class);
        Number tokenVersion = claims.get("tokenVersion", Number.class);
        return new UserPrincipal(uid.longValue(), claims.getSubject(), claims.get("name", String.class),
                role, "", true, Boolean.TRUE.equals(mustChangePassword),
                tokenVersion == null ? -1 : tokenVersion.intValue());
    }

    public long accessTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }

    public long refreshTtlSeconds() {
        return properties.refreshTtl().toSeconds();
    }
}
