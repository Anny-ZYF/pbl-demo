package com.gdmu.scoring;

import com.gdmu.common.ApiResponse;
import com.gdmu.security.UserPrincipal;
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
    public ApiResponse<Void> review(@AuthenticationPrincipal UserPrincipal teacher, @PathVariable long runId,
                                    @Valid @RequestBody ScoreReviewRequest request) {
        service.reviewAndApprove(teacher, runId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/scoring-runs/{runId}/publish")
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
