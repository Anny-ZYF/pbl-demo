package com.gdmu.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Organization nodes can be addressed either by id (CSV import and older
 * clients) or by name. A name wins over an id and is created on demand, so an
 * admin can add a new department, major or class while creating an account.
 * Classes are named text that is unique inside a department (for example
 * {@code 24莞信管01班}); there is no class number any more.
 */
public record AccountCreateRequest(
        @NotBlank String role,
        @NotBlank @Size(max = 20) String username,
        @JsonAlias("name") @NotBlank @Size(max = 50) String displayName,
        @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "初始密码需为8-64位且包含字母和数字")
        String initialPassword,
        Long departmentId, Long majorId,
        @Schema(description = "兼容字段：CSV 导入与旧客户端使用，必须属于所选学院；与 className 二选一，同时提供时以 className 为准")
        Long classId, Integer grade,
        @JsonAlias("classNumber")
        @Schema(description = "班级名称，学院内唯一（如 24莞信管01班）；服务端按 (departmentId, className) 复用或自动创建班级")
        @Size(max = 100) String className,
        @Schema(description = "学院名称，不存在时自动创建；与 departmentId 二选一，同时提供时以名称为准")
        @Size(max = 100) String departmentName,
        @Schema(description = "专业名称，学院内唯一，不存在时自动创建；与 majorId 二选一，同时提供时以名称为准")
        @Size(max = 100) String majorName) {

    public AccountCreateRequest(String role, String username, String displayName, String initialPassword,
                                Long departmentId, Long majorId, Long classId, Integer grade) {
        this(role, username, displayName, initialPassword, departmentId, majorId, classId, grade, null, null, null);
    }

    public AccountCreateRequest(String role, String username, String displayName, String initialPassword,
                                Long departmentId, Long majorId, Long classId, Integer grade, String className) {
        this(role, username, displayName, initialPassword, departmentId, majorId, classId, grade, className, null, null);
    }
}
