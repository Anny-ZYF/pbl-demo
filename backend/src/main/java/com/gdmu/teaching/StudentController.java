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

    @GetMapping("/assignments/{assignmentId}")
    public ApiResponse<Map<String, Object>> assignment(@AuthenticationPrincipal UserPrincipal student,
                                                       @PathVariable long assignmentId) {
        return ApiResponse.ok(service.studentAssignmentDetail(student, assignmentId));
    }

    @DeleteMapping("/assignments/{assignmentId}/attachments/{attachmentId}")
    public ApiResponse<Void> deleteAttachment(@AuthenticationPrincipal UserPrincipal student, @PathVariable long assignmentId, @PathVariable long attachmentId) {
        service.deleteStudentAttachment(student, assignmentId, attachmentId); return ApiResponse.ok(null);
    }

    @GetMapping("/groups")
    public ApiResponse<List<Map<String, Object>>> groups(@AuthenticationPrincipal UserPrincipal student) {
        return ApiResponse.ok(service.studentGroups(student));
    }

    @PostMapping("/assignments/{assignmentId}/groups")
    public ApiResponse<Map<String,Object>> createGroup(@AuthenticationPrincipal UserPrincipal student, @PathVariable long assignmentId) {
        return ApiResponse.created(service.createStudentGroup(student, assignmentId));
    }

    @PostMapping("/assignments/{assignmentId}/groups/join")
    public ApiResponse<Void> joinGroup(@AuthenticationPrincipal UserPrincipal student, @PathVariable long assignmentId, @Valid @RequestBody StudentGroupJoinRequest request) {
        service.joinStudentGroup(student, assignmentId, request.groupNo()); return ApiResponse.ok(null);
    }

    @GetMapping("/assignments/{assignmentId}/groups")
    public ApiResponse<List<Map<String,Object>>> assignmentGroups(@AuthenticationPrincipal UserPrincipal student, @PathVariable long assignmentId) {
        return ApiResponse.ok(service.studentAssignmentGroups(student, assignmentId));
    }

    @DeleteMapping("/assignments/{assignmentId}/groups/me")
    public ApiResponse<Void> leaveGroup(@AuthenticationPrincipal UserPrincipal student, @PathVariable long assignmentId) {
        service.leaveStudentGroup(student, assignmentId); return ApiResponse.ok(null);
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
