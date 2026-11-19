package com.gdmu.teaching;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 编辑作业的请求体。与创建请求字段一致，但截止时间不要求必须在未来：
 * 已结束的作业允许原样保存，只有把截止时间改到过去才会被服务层拒绝。
 */
@Schema(description = "编辑作业；评价分支需提交完整列表")
public record AssignmentUpdateRequest(
        @Schema(description = "作业名称") @NotBlank @Size(max = 200) String title,
        @Schema(description = "作业说明") @Size(max = 5000) String description,
        @Schema(description = "截止时间 ISO-8601") @NotNull LocalDateTime deadline,
        @Schema(description = "论文模型标准组规模 1-4") @Min(1) @Max(4) int standardGroupSize,
        @Schema(description = "作业总分 1-100") @DecimalMin("1") @DecimalMax("100") double totalScore,
        @Schema(description = "评价分支完整列表，权重之和为 1")
        @NotEmpty @Valid List<AssignmentCreateRequest.BranchRequest> branches) { }
