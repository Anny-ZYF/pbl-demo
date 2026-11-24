package com.gdmu.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * See {@link AccountCreateRequest}: organization nodes can be addressed by id
 * (older clients) or by name, and a name is created on demand. {@code classId},
 * {@code majorId} and {@code departmentId} are kept for backwards
 * compatibility.
 */
public record AccountUpdateRequest(@NotBlank String role, @JsonAlias("name") @NotBlank @Size(max = 50) String displayName,
                                   Long departmentId, Long majorId,
                                   @Schema(description = "兼容字段：必须属于所选学院；与 className 二选一，同时提供时以 className 为准")
                                   Long classId, Integer grade,
                                   @JsonAlias("classNumber")
                                   @Schema(description = "班级名称，学院内唯一（如 24莞信管01班）；服务端按 (departmentId, className) 复用或自动创建班级")
                                   @Size(max = 100) String className,
                                   @Schema(description = "学院名称，不存在时自动创建；与 departmentId 二选一，同时提供时以名称为准")
                                   @Size(max = 100) String departmentName,
                                   @Schema(description = "专业名称，学院内唯一，不存在时自动创建；与 majorId 二选一，同时提供时以名称为准")
                                   @Size(max = 100) String majorName) {

    public AccountUpdateRequest(String role, String displayName, Long departmentId, Long majorId,
                                Long classId, Integer grade) {
        this(role, displayName, departmentId, majorId, classId, grade, null, null, null);
    }

    public AccountUpdateRequest(String role, String displayName, Long departmentId, Long majorId,
                                Long classId, Integer grade, String className) {
        this(role, displayName, departmentId, majorId, classId, grade, className, null, null);
    }
}
