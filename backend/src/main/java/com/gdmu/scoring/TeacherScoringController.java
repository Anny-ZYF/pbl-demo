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
    public ApiResponse<Void> grade(@AuthenticationPrincipal UserPrincipal teacher, @PathVariable long submissionId,
                                   @Valid @RequestBody GradeSubmissionRequest request) {
        service.gradeSubmission(teacher, submissionId, request);
        return ApiResponse.ok(null);
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

    @PutMapping("/scoring-runs/{runId}/review")
    @Operation(summary = "逐人复核本次评分运行", description = "必须提交本次运行的全部成员成绩；调整建议分必须填写理由。只有 MODEL_COMPLETE 或 DEGRADED 可复核，成功后进入 APPROVED。")
    public ApiResponse<Void> review(@AuthenticationPrincipal UserPrincipal teacher, @PathVariable long runId,
                                    @Valid @RequestBody ScoreReviewRequest request) {
        service.reviewAndApprove(teacher, runId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/scoring-runs/{runId}/publish")
    @Operation(summary = "发布本次已复核的评分运行", description = "仅发布路径中指定的运行及其成员结果，不影响同作业其他运行。运行须为 APPROVED 且全部结果为 REVIEWED，否则返回 409；不属于当前教师返回 404。重复发布已发布的运行返回 200，不改写时间、投影或审计。")
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
