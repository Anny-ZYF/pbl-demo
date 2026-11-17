package com.gdmu.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Class numbers are scoped to a department, not to a major. A student's major
 * and class are two independent organization dimensions under the same
 * department. {@code classNumber} is the preferred input for the admin UI;
 * {@code classId} remains supported for CSV import and older clients.
 */
public record AccountCreateRequest(
        @NotBlank String role,
        @NotBlank @Size(max = 20) String username,
        @JsonAlias("name") @NotBlank @Size(max = 50) String displayName,
        @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "初始密码需为8-64位且包含字母和数字")
        String initialPassword,
        Long departmentId, Long majorId,
        @Schema(description = "兼容字段：CSV 导入与旧客户端使用，必须属于所选学院；与 classNumber 二选一，同时提供时以 classNumber 为准")
        Long classId, Integer grade,
        @Schema(description = "学院维度统一编号的班级号，与 majorId 无关；服务端按 (departmentId, classNumber) 复用或自动创建班级，不设固定数量上限")
        @Min(1) @Max(9999) Integer classNumber) {

    public AccountCreateRequest(String role, String username, String displayName, String initialPassword,
                                Long departmentId, Long majorId, Long classId, Integer grade) {
        this(role, username, displayName, initialPassword, departmentId, majorId, classId, grade, null);
    }
}
