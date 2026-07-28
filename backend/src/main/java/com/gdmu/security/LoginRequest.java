package com.gdmu.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空") @Size(max = 50) String username,
        @NotBlank(message = "密码不能为空") @Size(max = 100) String password,
        String role) {
}
