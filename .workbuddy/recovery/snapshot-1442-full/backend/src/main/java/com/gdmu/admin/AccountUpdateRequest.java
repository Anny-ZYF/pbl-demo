package com.gdmu.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * See {@link AccountCreateRequest}: {@code classNumber} is a department-scoped
 * class number, independent of the selected major. {@code classId} is kept for
 * backwards compatibility.
 */
public record AccountUpdateRequest(@NotBlank String role, @JsonAlias("name") @NotBlank @Size(max = 50) String displayName,
                                   Long departmentId, Long majorId,
                                   @Schema(description = "兼容字段：必须属于所选学院；与 classNumber 二选一，同时提供时以 classNumber 为准")
                                   Long classId, Integer grade,
                                   @Schema(description = "学院维度统一编号的班级号，与 majorId 无关；服务端按 (departmentId, classNumber) 复用或自动创建班级，不设固定数量上限")
                                   @Min(1) @Max(9999) Integer classNumber) {

    public AccountUpdateRequest(String role, String displayName, Long departmentId, Long majorId,
                                Long classId, Integer grade) {
        this(role, displayName, departmentId, majorId, classId, grade, null);
    }
}
