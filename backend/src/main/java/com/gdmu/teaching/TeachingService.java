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
        auditService.record(teacher, "ASSIGNMENT_PUBLISHED", "ASSIGNMENT", assignmentId, Map.of("courseId", courseId));
        return Map.of("id", assignmentId, "status", "PUBLISHED");
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
        List<Map<String, Object>> submissions = mapper.submissions(assignmentId, teacher.id());
        for (Map<String, Object> submission : submissions) {
            Object id = submission.get("id");
            if (id instanceof Number number) {
                submission.put("attachments", mapper.teacherSubmissionAttachments(number.longValue(), teacher.id()));
            }
        }
        return submissions;
    }

    public List<Map<String, Object>> studentCourses(UserPrincipal student) {
        return mapper.studentCourses(student.id());
    }

    public List<Map<String, Object>> studentAssignments(UserPrincipal student, long courseId) {
        return mapper.studentAssignments(courseId, student.id());
    }

    public Map<String, Object> studentAssignmentDetail(UserPrincipal student, long assignmentId) {
        Map<String, Object> detail = mapper.studentAssignmentDetail(assignmentId, student.id());
        require(detail != null, HttpStatus.NOT_FOUND, "作业不存在或当前学生无权查看");
        Object groupId = detail.get("groupId");
        detail.put("members", groupId == null ? List.of() : mapper.studentGroupMembers(
                ((Number) groupId).longValue(), assignmentId));
        detail.put("parts", mapper.studentAssignmentParts(assignmentId));
        detail.put("attachments", mapper.studentSubmissionAttachments(assignmentId, student.id()));
        return detail;
    }

    @Transactional
    public void deleteStudentAttachment(UserPrincipal student, long assignmentId, long attachmentId) {
        require(mapper.deleteStudentAttachment(attachmentId, assignmentId, student.id()) == 1, HttpStatus.NOT_FOUND, "附件不存在或无权删除");
    }

    public List<Map<String, Object>> studentGroups(UserPrincipal student) {
        return mapper.studentGroups(student.id());
    }

    public List<Map<String,Object>> studentAssignmentGroups(UserPrincipal student, long assignmentId) {
        require(mapper.assignmentCourseForStudent(assignmentId, student.id()) != null, HttpStatus.NOT_FOUND, "作业不存在或未选修该课程");
        return mapper.studentAssignmentGroups(assignmentId);
    }

    @Transactional
    public void leaveStudentGroup(UserPrincipal student, long assignmentId) {
        LocalDateTime publishedAt = mapper.assignmentPublishedAt(assignmentId);
        require(publishedAt != null && LocalDateTime.now().isBefore(publishedAt.plusDays(2)), HttpStatus.CONFLICT, "组队期已结束，小组已经锁定");
        Long groupId = mapper.studentGroup(assignmentId, student.id());
        require(groupId != null, HttpStatus.CONFLICT, "当前没有加入小组");
        require(mapper.leaveStudentGroup(groupId, student.id()) == 1, HttpStatus.CONFLICT, "退出小组失败");
        mapper.dissolveEmptyGroup(groupId);
    }

    @Transactional
    public Map<String,Object> createStudentGroup(UserPrincipal student, long assignmentId) {
        Long courseId = mapper.assignmentCourseForStudent(assignmentId, student.id());
        Integer standard = mapper.studentAssignmentGroupSize(assignmentId);
        require(courseId != null && standard != null, HttpStatus.NOT_FOUND, "作业不存在或未发布");
        int no = mapper.nextGroupNo(assignmentId);
        int max = standard + 1;
        mapper.insertStudentGroup("第" + no + "组", courseId, assignmentId, student.id(), max);
        long groupId = mapper.lastGroupId(); mapper.insertStudentLeader(groupId, assignmentId, student.id());
        return Map.of("id", groupId, "name", "第" + no + "组", "maxMembers", max);
    }

    @Transactional
    public void joinStudentGroup(UserPrincipal student, long assignmentId, int groupNo) {
        Long courseId = mapper.assignmentCourseForStudent(assignmentId, student.id());
        require(courseId != null, HttpStatus.NOT_FOUND, "作业不存在或未选修该课程");
        Map<String,Object> group = mapper.findStudentGroup(assignmentId, "第" + groupNo + "组");
        require(group != null, HttpStatus.NOT_FOUND, "小组不存在");
        require(mapper.studentGroup(assignmentId, student.id()) == null, HttpStatus.CONFLICT, "你已经加入该作业的小组");
        require(mapper.activeGroupMemberCount(((Number) group.get("id")).longValue()) < ((Number) group.get("maxMembers")).intValue(), HttpStatus.CONFLICT, "小组人数已满");
        mapper.joinStudentGroup(((Number) group.get("id")).longValue(), assignmentId, student.id());
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
        LocalDateTime publishedAt = mapper.assignmentPublishedAt(assignmentId);
        require(publishedAt != null && !LocalDateTime.now().isBefore(publishedAt.plusDays(2)), HttpStatus.CONFLICT, "当前处于组队期，暂不可提交作业");
        require(deadline != null, HttpStatus.CONFLICT, "作业未发布");
        require(!LocalDateTime.now().isAfter(deadline), HttpStatus.CONFLICT, "作业已截止");
        saveSubmissionContributions(student, assignmentId, groupId, request);
        require((request.content() != null && !request.content().isBlank())
                        || (request.filePath() != null && !request.filePath().isBlank())
                        || (request.attachments() != null && !request.attachments().isEmpty()),
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
        Long submissionId = mapper.latestSubmissionId(assignmentId, groupId);
        if (submissionId != null && request.attachments() != null) {
            for (String key : request.attachments()) {
                if (key == null || key.isBlank()) continue;
                String normalized = key.replace('\\', '/');
                require(normalized.matches("\\d{4}/\\d{2}/student/" + student.id() + "/[0-9a-fA-F-]{36}\\.[A-Za-z0-9]{1,8}"), HttpStatus.BAD_REQUEST, "附件路径不合法");
                String name = normalized.substring(normalized.lastIndexOf('/') + 1);
                mapper.insertAttachment(submissionId, normalized, name, 0);
            }
        }
        auditService.record(student, "SUBMISSION_SAVED", "ASSIGNMENT", assignmentId, Map.of("groupId", groupId));
    }

    private void saveSubmissionContributions(UserPrincipal student, long assignmentId, long groupId,
                                             StudentSubmissionRequest request) {
        List<Long> members = mapper.activeGroupStudentIds(groupId, assignmentId);
        Map<Long, java.math.BigDecimal> requested = request.contributionScores();
        require(requested != null && requested.keySet().equals(new java.util.LinkedHashSet<>(members)),
                HttpStatus.BAD_REQUEST, "请为全部小组成员填写贡献值");
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (Long memberId : members) {
            java.math.BigDecimal value = requested.get(memberId);
            require(value != null && value.compareTo(java.math.BigDecimal.ZERO) >= 0
                            && value.compareTo(java.math.BigDecimal.valueOf(100)) <= 0,
                    HttpStatus.BAD_REQUEST, "贡献值必须在0到100之间");
            total = total.add(value);
        }
        require(total.subtract(java.math.BigDecimal.valueOf(100)).abs()
                        .compareTo(java.math.BigDecimal.valueOf(0.01)) <= 0,
                HttpStatus.BAD_REQUEST, "贡献值总和必须为100%");
        int version = mapper.nextSubmissionWorkloadVersion(groupId, assignmentId);
        mapper.supersedeSubmissionWorkloads(groupId, assignmentId);
        mapper.insertSubmissionWorkloadSet(groupId, assignmentId, version, student.id());
        long setId = mapper.submissionWorkloadSetId(groupId, assignmentId, version);
        for (Long partId : mapper.assignmentPartIds(assignmentId)) {
            for (Long memberId : members) {
                mapper.insertSubmissionWorkloadItem(setId, partId, memberId,
                        requested.get(memberId).divide(java.math.BigDecimal.valueOf(100), 6,
                                java.math.RoundingMode.HALF_UP));
            }
        }
        auditService.record(student, "SUBMISSION_CONTRIBUTIONS_SAVED", "WORKLOAD_SET", setId,
                Map.of("assignmentId", assignmentId, "groupId", groupId));
    }

    private void require(boolean condition, HttpStatus status, String message) {
        if (!condition) throw new BusinessException(status, message);
    }
}
