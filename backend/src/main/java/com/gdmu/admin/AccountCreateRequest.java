package com.gdmu.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AccountCreateRequest(
        @NotBlank String role,
        @NotBlank @Size(max = 50) String username,
        @JsonAlias("name") @NotBlank @Size(max = 50) String displayName,
        @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "初始密码需为8-64位且包含字母和数字")
        String initialPassword,
        Long departmentId, Long majorId, Long classId, Integer grade) {
}
