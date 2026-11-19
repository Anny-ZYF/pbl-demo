package com.gdmu.security;

import com.gdmu.common.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public enum Role {
    ADMIN, TEACHER, STUDENT;

    public static Role parse(String value) {
        try {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (normalized.startsWith("ROLE_")) normalized = normalized.substring(5);
            return Role.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "角色必须是 ADMIN、TEACHER 或 STUDENT");
        }
    }
}
