package com.gdmu.teaching;

import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.UserPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TeachingService {
    private static final double EPSILON = 1e-6;
    private final TeachingMapper mapper;
    private final AuditService auditService;

    public TeachingService(TeachingMapper mapper, AuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> teacherCourses(UserPrincipal teacher) {
        return mapper.teacherCourses(teacher.id());
    }

    public List<Map<String, Object>> teacherAssignments(UserPrincipal teacher, long courseId) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        return mapper.teacherAssignments(courseId, teacher.id());
    }

    public List<Map<String, Object>> courseStudents(UserPrincipal teacher, long courseId, Long assignmentId) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        if (assignmentId != null) {
            Long assignmentCourseId = mapper.assignmentCourse(assignmentId, teacher.id());
            require(assignmentCourseId != null && assignmentCourseId == courseId, HttpStatus.BAD_REQUEST,
                    "作业不属于该课程");
        }
        return mapper.courseStudents(courseId, assignmentId);
    }

    public List<Map<String, Object>> teacherScores(UserPrincipal teacher, long courseId, long assignmentId) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        Long assignmentCourse = mapper.assignmentCourse(assignmentId, teacher.id());
        require(assignmentCourse != null && assignmentCourse == courseId, HttpStatus.BAD_REQUEST, "作业不属于该课程");
        return mapper.teacherScores(courseId, assignmentId);
    }

    @Transactional
    public StudentCourseUpdateResult importStudents(UserPrincipal teacher, long courseId, StudentImportRequest request) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        int success = 0, skipped = 0;
        List<String> notFound = new ArrayList<>();
        for (String rawNo : new LinkedHashSet<>(request.studentNos())) {
            String studentNo = rawNo == null ? "" : rawNo.trim();
            Long studentId = mapper.studentIdByNo(studentNo);
            if (studentId == null) { notFound.add(studentNo); continue; }
            String status = mapper.studentCourseStatus(studentId, courseId);
            if ("ACTIVE".equals(status)) { skipped++; continue; }
            mapper.importStudent(studentId, courseId); success++;
        }
        auditService.record(teacher, "STUDENTS_IMPORTED", "COURSE", courseId,
                Map.of("success", success, "skipped", skipped, "notFound", notFound.size()));
        return new StudentCourseUpdateResult(success, skipped, notFound);
    }

    @Transactional
    public void dropStudent(UserPrincipal teacher, long courseId, long studentId) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        require(mapper.dropStudent(studentId, courseId) == 1, HttpStatus.CONFLICT, "学生不在当前课程中或已经被移除");
        auditService.record(teacher, "STUDENT_DROPPED", "COURSE", courseId, Map.of("studentId", studentId));
    }

    @Transactional
    public Map<String, Object> createAssignment(UserPrincipal teacher, long courseId, AssignmentCreateRequest request) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        double sum = request.branches().stream().mapToDouble(AssignmentCreateRequest.BranchRequest::weight).sum();
        require(Math.abs(sum - 1.0) <= EPSILON, HttpStatus.BAD_REQUEST, "评价分支权重之和必须为1");
        long distinctNames = request.branches().stream().map(b -> b.name().trim().toLowerCase()).distinct().count();
        require(distinctNames == request.branches().size(), HttpStatus.BAD_REQUEST, "评价分支名称不能重复");
        mapper.insertAssignment(courseId, teacher.id(), request);
        long assignmentId = mapper.lastInsertId();
        for (int i = 0; i < request.branches().size(); i++) {
            var branch = request.branches().get(i);
            mapper.insertAssignmentPart(assignmentId, branch.name().trim(), branch.description(), branch.weight(), i + 1);
        }
        auditService.record(teacher, "ASSIGNMENT_CREATED", "ASSIGNMENT", assignmentId,
                Map.of("courseId", courseId, "standardGroupSize", request.standardGroupSize(), "branchCount", request.branches().size()));
        return Map.of("id", assignmentId, "status", "DRAFT");
    }

    @Transactional
    public void publishAssignment(UserPrincipal teacher, long assignmentId) {
        require(mapper.publishAssignment(assignmentId, teacher.id()) == 1, HttpStatus.CONFLICT,
                "作业不存在、不属于当前教师或不是草稿状态");
        auditService.record(teacher, "ASSIGNMENT_PUBLISHED", "ASSIGNMENT", assignmentId, Map.of());
    }

    public List<Map<String, Object>> groups(UserPrincipal teacher, long assignmentId) {
        require(mapper.ownsAssignment(assignmentId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "作业不存在或不属于当前教师");
        return mapper.groups(assignmentId, teacher.id());
    }

    public List<Map<String, Object>> groupMembers(UserPrincipal teacher, long assignmentId, long groupId) {
        require(mapper.ownsGroup(groupId, assignmentId, teacher.id()) == 1, HttpStatus.NOT_FOUND,
                "团队不存在或不属于当前教师作业");
        return mapper.groupMembers(groupId);
    }

    @Transactional
    public Map<String, Object> createGroup(UserPrincipal teacher, long assignmentId, GroupCreateRequest request) {
        Long courseId = mapper.assignmentCourse(assignmentId, teacher.id());
        Integer p = mapper.assignmentStandardGroupSize(assignmentId, teacher.id());
        require(courseId != null && p != null, HttpStatus.NOT_FOUND, "作业不存在或不属于当前教师");
        LinkedHashSet<Long> memberSet = new LinkedHashSet<>(request.memberStudentIds());
        memberSet.add(request.leaderStudentId());
        List<Long> members = new ArrayList<>(memberSet);
        require(members.size() <= p + 1, HttpStatus.BAD_REQUEST, "团队人数不能超过标准组规模+1");
        require(mapper.eligibleStudentCount(courseId, members) == members.size(), HttpStatus.BAD_REQUEST,
                "成员必须全部来自该课程的有效名单");
        require(mapper.existingAssignmentMemberships(assignmentId, members) == 0, HttpStatus.CONFLICT,
                "成员中有人已加入该作业的其他团队");
        try {
            mapper.insertGroup(assignmentId, courseId, members.size(), request);
            long groupId = mapper.lastInsertId();
            for (Long studentId : members) {
                mapper.insertGroupMember(groupId, assignmentId, studentId,
                        studentId == request.leaderStudentId() ? "LEADER" : "MEMBER");
            }
            auditService.record(teacher, "GROUP_CREATED", "GROUP", groupId,
                    Map.of("assignmentId", assignmentId, "memberCount", members.size()));
            return Map.of("id", groupId, "memberCount", members.size(), "status", "CONFIRMED");
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "组名重复或学生已加入该作业的其他团队");
        }
    }

    public List<Map<String, Object>> submissions(UserPrincipal teacher, long assignmentId) {
        require(mapper.ownsAssignment(assignmentId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "作业不存在或不属于当前教师");
        return mapper.submissions(assignmentId, teacher.id());
    }

    public List<Map<String, Object>> studentCourses(UserPrincipal student) {
        return mapper.studentCourses(student.id());
    }

    public List<Map<String, Object>> studentAssignments(UserPrincipal student, long courseId) {
        return mapper.studentAssignments(courseId, student.id());
    }

    public List<Map<String, Object>> studentGroups(UserPrincipal student) {
        return mapper.studentGroups(student.id());
    }

    public List<Map<String, Object>> studentGroupMembers(UserPrincipal student, long groupId) {
        require(mapper.studentOwnsGroup(groupId, student.id()) == 1, HttpStatus.NOT_FOUND,
                "团队不存在或当前学生不是有效成员");
        return mapper.groupMembers(groupId);
    }

    @Transactional
    public void submit(UserPrincipal student, long assignmentId, StudentSubmissionRequest request) {
        Long groupId = mapper.studentGroup(assignmentId, student.id());
        require(groupId != null, HttpStatus.FORBIDDEN, "当前学生不属于该作业的有效团队");
        LocalDateTime deadline = mapper.assignmentDeadline(assignmentId);
        require(deadline != null, HttpStatus.CONFLICT, "作业未发布");
        require(!LocalDateTime.now().isAfter(deadline), HttpStatus.CONFLICT, "作业已截止");
        require((request.content() != null && !request.content().isBlank())
                        || (request.filePath() != null && !request.filePath().isBlank()),
                HttpStatus.BAD_REQUEST, "正文和附件至少提交一项");
        if (request.filePath() != null && !request.filePath().isBlank()) {
            String normalized = request.filePath().replace('\\', '/');
            require(normalized.matches("\\d{4}/\\d{2}/student/" + student.id()
                            + "/[0-9a-fA-F-]{36}\\.[A-Za-z0-9]{1,8}"),
                    HttpStatus.BAD_REQUEST, "附件必须由当前学生账号上传");
        }
        Map<String, Object> existing = mapper.lockSubmission(assignmentId, groupId);
        if (existing != null) {
            String status = String.valueOf(existing.get("status"));
            Object publishedValue = existing.get("isScorePublished");
            boolean published = publishedValue instanceof Boolean booleanValue ? booleanValue
                    : publishedValue instanceof Number numberValue && numberValue.intValue() == 1;
            require(!published && (status.equals("DRAFT") || status.equals("SUBMITTED") || status.equals("RETURNED")),
                    HttpStatus.CONFLICT, "作品已评分或成绩已发布，不能覆盖提交");
        }
        mapper.upsertSubmission(assignmentId, groupId, student.id(), request);
        auditService.record(student, "SUBMISSION_SAVED", "ASSIGNMENT", assignmentId, Map.of("groupId", groupId));
    }

    private void require(boolean condition, HttpStatus status, String message) {
        if (!condition) throw new BusinessException(status, message);
    }
}
