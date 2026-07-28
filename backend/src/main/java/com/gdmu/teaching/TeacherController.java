package com.gdmu.teaching;

import com.gdmu.common.ApiResponse;
import com.gdmu.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherController {
    private final TeachingService service;

    public TeacherController(TeachingService service) {
        this.service = service;
    }

    @GetMapping("/courses")
    public ApiResponse<List<Map<String, Object>>> courses(@AuthenticationPrincipal UserPrincipal teacher) {
        return ApiResponse.ok(service.teacherCourses(teacher));
    }

    @GetMapping("/courses/{courseId}/assignments")
    public ApiResponse<List<Map<String, Object>>> assignments(@AuthenticationPrincipal UserPrincipal teacher,
                                                              @PathVariable long courseId) {
        return ApiResponse.ok(service.teacherAssignments(teacher, courseId));
    }

    @GetMapping("/courses/{courseId}/students")
    public ApiResponse<List<Map<String, Object>>> courseStudents(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long courseId,
            @RequestParam(required = false) Long assignmentId) {
        return ApiResponse.ok(service.courseStudents(teacher, courseId, assignmentId));
    }

    @PostMapping("/courses/{courseId}/assignments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAssignment(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long courseId,
            @Valid @RequestBody AssignmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.createAssignment(teacher, courseId, request)));
    }

    @PostMapping("/assignments/{assignmentId}/publish")
    public ApiResponse<Void> publishAssignment(@AuthenticationPrincipal UserPrincipal teacher,
                                               @PathVariable long assignmentId) {
        service.publishAssignment(teacher, assignmentId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/assignments/{assignmentId}/groups")
    public ApiResponse<List<Map<String, Object>>> groups(@AuthenticationPrincipal UserPrincipal teacher,
                                                        @PathVariable long assignmentId) {
        return ApiResponse.ok(service.groups(teacher, assignmentId));
    }

    @GetMapping("/assignments/{assignmentId}/groups/{groupId}/members")
    public ApiResponse<List<Map<String, Object>>> groupMembers(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long assignmentId,
            @PathVariable long groupId) {
        return ApiResponse.ok(service.groupMembers(teacher, assignmentId, groupId));
    }

    @PostMapping("/assignments/{assignmentId}/groups")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createGroup(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long assignmentId,
            @Valid @RequestBody GroupCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.createGroup(teacher, assignmentId, request)));
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    public ApiResponse<List<Map<String, Object>>> submissions(@AuthenticationPrincipal UserPrincipal teacher,
                                                             @PathVariable long assignmentId) {
        return ApiResponse.ok(service.submissions(teacher, assignmentId));
    }
}
