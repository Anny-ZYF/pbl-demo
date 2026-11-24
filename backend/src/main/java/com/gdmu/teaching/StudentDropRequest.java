package com.gdmu.teaching;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 课程学生批量移除请求。移除只把 {@code student_course.status} 置为 DROPPED，
 * 不删除账号，之后仍可通过「导入学生」重新加入，因此可以安全地一次提交多人。
 */
@Schema(description = "批量移除课程学生：仅移出当前课程，不删除学生账号")
public record StudentDropRequest(
        @Schema(description = "要移出课程的学生 ID，最多 500 个")
        @NotEmpty @Size(max = 500) List<@Positive Long> studentIds) {
}
