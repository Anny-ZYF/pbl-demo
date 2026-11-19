package com.gdmu.teaching;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 课程学生导入请求。班级库是主入口（整班批量导入），学号列表保留给个别补录
 * 以及旧客户端。两者可以同时提供，服务端按学生 ID 去重后合并处理。
 */
@Schema(description = "课程学生导入：按系统班级库批量导入，或按学号补充导入；两者至少提供一个")
public record StudentImportRequest(
        @Schema(description = "系统班级库中的班级 ID，最多 50 个；导入其下全部有效学生账号")
        @Size(max = 50) List<@Positive Long> classIds,

        @Schema(description = "学号列表，最多 500 个；兼容旧客户端与个别补录")
        @Size(max = 500) List<@Size(max = 20) String> studentNos) {

    /** 旧客户端只发送 {@code studentNos} 时使用的兼容构造。 */
    public StudentImportRequest(List<String> studentNos) {
        this(null, studentNos);
    }

    public List<Long> classIdsOrEmpty() {
        return classIds == null ? List.of() : classIds;
    }

    public List<String> studentNosOrEmpty() {
        return studentNos == null ? List.of() : studentNos;
    }
}
