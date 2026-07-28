package com.gdmu.teaching;

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
public class StudentController {
    private final TeachingService service;

    public StudentController(TeachingService service) {
        this.service = service;
    }

    @GetMapping("/courses")
    public ApiResponse<List<Map<String, Object>>> courses(@AuthenticationPrincipal UserPrincipal student) {
        return ApiResponse.ok(service.studentCourses(student));
    }

    @GetMapping("/courses/{courseId}/assignments")
    public ApiResponse<List<Map<String, Object>>> assignments(@AuthenticationPrincipal UserPrincipal student,
                                                              @PathVariable long courseId) {
        return ApiResponse.ok(service.studentAssignments(student, courseId));
    }

    @GetMapping("/groups")
    public ApiResponse<List<Map<String, Object>>> groups(@AuthenticationPrincipal UserPrincipal student) {
        return ApiResponse.ok(service.studentGroups(student));
    }

    @GetMapping("/groups/{groupId}/members")
    public ApiResponse<List<Map<String, Object>>> groupMembers(
            @AuthenticationPrincipal UserPrincipal student, @PathVariable long groupId) {
        return ApiResponse.ok(service.studentGroupMembers(student, groupId));
    }

    @PostMapping("/assignments/{assignmentId}/submission")
    public ApiResponse<Void> submit(@AuthenticationPrincipal UserPrincipal student, @PathVariable long assignmentId,
                                    @Valid @RequestBody StudentSubmissionRequest request) {
        service.submit(student, assignmentId, request);
        return ApiResponse.ok(null);
    }
}
