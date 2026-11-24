package com.gdmu.scoring;

import com.gdmu.common.ApiResponse;
import com.gdmu.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherScoringController {
    private final ScoringService service;

    public TeacherScoringController(ScoringService service) {
        this.service = service;
    }

    @PutMapping("/submissions/{submissionId}/grade")
    @Operation(summary = "录入团队成绩", description = "保存团队成绩后立即按分工比例运行一次评分，返回本次运行与个人成绩条数；评分前置条件不满足时只跳过评分，团队成绩仍会保存。")
    public ApiResponse<Map<String, Object>> grade(@AuthenticationPrincipal UserPrincipal teacher,
                                                  @PathVariable long submissionId,
                                                  @Valid @RequestBody GradeSubmissionRequest request) {
        return ApiResponse.ok(service.gradeSubmission(teacher, submissionId, request));
    }

    @PutMapping("/assignments/{assignmentId}/groups/{groupId}/workload")
    public ApiResponse<Map<String, Object>> createWorkload(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long assignmentId,
            @PathVariable long groupId, @Valid @RequestBody WorkloadAllocationRequest request) {
        return ApiResponse.ok(service.createWorkload(teacher, assignmentId, groupId, request));
    }

    @GetMapping("/assignments/{assignmentId}/groups/{groupId}/workload")
    public ApiResponse<Map<String, Object>> workload(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long assignmentId,
            @PathVariable long groupId) {
        return ApiResponse.ok(service.teacherWorkload(teacher, assignmentId, groupId));
    }

    @PostMapping("/submissions/{submissionId}/scoring-runs")
    public ApiResponse<Map<String, Object>> runScoring(@AuthenticationPrincipal UserPrincipal teacher,
                                                       @PathVariable long submissionId) {
        return ApiResponse.ok(service.runScoring(teacher, submissionId));
    }

    @GetMapping("/scoring-runs/{runId}")
    public ApiResponse<Map<String, Object>> run(@AuthenticationPrincipal UserPrincipal teacher,
                                                @PathVariable long runId) {
        return ApiResponse.ok(service.runDetails(teacher, runId));
    }

    @PostMapping("/scoring-runs/{runId}/publish")
    @Operation(summary = "发布评分运行", description = "教师的唯一发布入口：MODEL_COMPLETE/DEGRADED 的运行会先按算法分自动落为已复核再发布，APPROVED 的直接发布；仅影响路径中指定的运行。已发布重复调用返回 200，不改写时间、投影或审计。")
    public ApiResponse<Void> publish(@AuthenticationPrincipal UserPrincipal teacher, @PathVariable long runId) {
        service.publish(teacher, runId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/appeals")
    public ApiResponse<List<Map<String, Object>>> appeals(@AuthenticationPrincipal UserPrincipal teacher) {
        return ApiResponse.ok(service.teacherAppeals(teacher));
    }

    @PutMapping("/appeals/{appealId}")
    public ApiResponse<Void> resolveAppeal(@AuthenticationPrincipal UserPrincipal teacher,
                                           @PathVariable long appealId,
                                           @Valid @RequestBody AppealResolveRequest request) {
        service.resolveAppeal(teacher, appealId, request);
        return ApiResponse.ok(null);
    }
}
