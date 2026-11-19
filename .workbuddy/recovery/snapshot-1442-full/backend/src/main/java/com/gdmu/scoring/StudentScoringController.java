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
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentScoringController {
    private final ScoringService service;

    public StudentScoringController(ScoringService service) {
        this.service = service;
    }

    @GetMapping("/assignments/{assignmentId}/workload")
    public ApiResponse<Map<String, Object>> workload(@AuthenticationPrincipal UserPrincipal student,
                                                     @PathVariable long assignmentId) {
        return ApiResponse.ok(service.studentWorkload(student, assignmentId));
    }

    @PutMapping("/assignments/{assignmentId}/groups/{groupId}/workload")
    public ApiResponse<Map<String, Object>> proposeWorkload(
            @AuthenticationPrincipal UserPrincipal student, @PathVariable long assignmentId,
            @PathVariable long groupId, @Valid @RequestBody WorkloadAllocationRequest request) {
        return ApiResponse.ok(service.proposeWorkload(student, assignmentId, groupId, request));
    }

    @PutMapping("/workload-sets/{setId}/confirmation")
    public ApiResponse<Void> confirmWorkload(@AuthenticationPrincipal UserPrincipal student, @PathVariable long setId,
                                             @Valid @RequestBody WorkloadConfirmationRequest request) {
        service.confirmWorkload(student, setId, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/scores")
    public ApiResponse<List<Map<String, Object>>> scores(@AuthenticationPrincipal UserPrincipal student) {
        return ApiResponse.ok(service.studentScores(student));
    }

    @GetMapping("/appeals")
    public ApiResponse<List<Map<String, Object>>> appeals(@AuthenticationPrincipal UserPrincipal student) {
        return ApiResponse.ok(service.studentAppeals(student));
    }

    @PostMapping("/scores/{scoreResultId}/appeals")
    public ApiResponse<Void> appeal(@AuthenticationPrincipal UserPrincipal student, @PathVariable long scoreResultId,
                                    @Valid @RequestBody AppealCreateRequest request) {
        service.appeal(student, scoreResultId, request);
        return ApiResponse.ok(null);
    }
}
