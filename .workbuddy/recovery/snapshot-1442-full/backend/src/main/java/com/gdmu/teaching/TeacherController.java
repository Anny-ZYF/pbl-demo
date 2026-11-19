package com.gdmu.teaching;

import com.gdmu.common.ApiResponse;
import com.gdmu.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
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

    @PostMapping("/courses")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCourse(
            @AuthenticationPrincipal UserPrincipal teacher,
            @Valid @RequestBody CourseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.createCourse(teacher, request)));
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

    @GetMapping("/courses/{courseId}/scores")
    public ApiResponse<List<Map<String, Object>>> scores(@AuthenticationPrincipal UserPrincipal teacher,
                                                         @PathVariable long courseId,
                                                         @RequestParam long assignmentId) {
        return ApiResponse.ok(service.teacherScores(teacher, courseId, assignmentId));
    }

    @Operation(summary = "查询系统班级库，供课程按班级批量导入学生")
    @GetMapping("/courses/{courseId}/classes")
    public ApiResponse<List<Map<String, Object>>> importableClasses(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long courseId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.importableClasses(teacher, courseId, keyword));
    }

    @Operation(summary = "预览某班级的学生名册及其在当前课程的导入状态")
    @GetMapping("/courses/{courseId}/classes/{classId}/students")
    public ApiResponse<List<Map<String, Object>>> classRoster(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long courseId,
            @PathVariable long classId) {
        return ApiResponse.ok(service.classRoster(teacher, courseId, classId));
    }

    @Operation(summary = "批量导入学生：按班级库整班导入，或按学号补充导入")
    @PostMapping("/courses/{courseId}/students/import")
    public ApiResponse<StudentCourseUpdateResult> importStudents(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long courseId,
            @Valid @RequestBody StudentImportRequest request) {
        return ApiResponse.ok(service.importStudents(teacher, courseId, request));
    }

    @DeleteMapping("/courses/{courseId}/students/{studentId}")
    public ApiResponse<Void> dropStudent(@AuthenticationPrincipal UserPrincipal teacher,
                                         @PathVariable long courseId, @PathVariable long studentId) {
        service.dropStudent(teacher, courseId, studentId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/courses/{courseId}/assignments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAssignment(
            @AuthenticationPrincipal UserPrincipal teacher, @PathVariable long courseId,
            @Valid @RequestBody AssignmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(service.createAssignment(teacher, courseId, request)));
    }

    @Operation(summary = "查看作业详情，含评价分支、下游数据锁定状态与已发布成绩状态")
    @GetMapping("/assignments/{assignmentId}")
    public ApiResponse<Map<String, Object>> assignmentDetail(@AuthenticationPrincipal UserPrincipal teacher,
                                                             @PathVariable long assignmentId) {
        return ApiResponse.ok(service.teacherAssignmentDetail(teacher, assignmentId));
    }

    @Operation(summary = "编辑作业；已有团队、工作量、提交或评分记录时锁定评价分支与标准人数")
    @PutMapping("/assignments/{assignmentId}")
    public ApiResponse<Void> updateAssignment(@AuthenticationPrincipal UserPrincipal teacher,
                                              @PathVariable long assignmentId,
                                              @Valid @RequestBody AssignmentUpdateRequest request) {
        service.updateAssignment(teacher, assignmentId, request);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除作业：归档而不物理删除，学生端不再可见，提交、评分与审计记录保留")
    @DeleteMapping("/assignments/{assignmentId}")
    public ApiResponse<Void> deleteAssignment(@AuthenticationPrincipal UserPrincipal teacher,
                                              @PathVariable long assignmentId) {
        service.archiveAssignment(teacher, assignmentId);
        return ApiResponse.ok(null);
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
