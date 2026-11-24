package com.gdmu.teaching;

import com.gdmu.audit.AuditService;
import com.gdmu.common.AttachmentNames;
import com.gdmu.common.BusinessException;
import com.gdmu.security.UserPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TeachingService {
    private static final double EPSILON = 1e-6;
    /** 单次导入的学生上限，避免一次勾选过多班级造成超长事务。 */
    private static final int MAX_IMPORT_STUDENTS = 2000;
    private final TeachingMapper mapper;
    private final AuditService auditService;

    public TeachingService(TeachingMapper mapper, AuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> teacherCourses(UserPrincipal teacher) {
        return mapper.teacherCourses(teacher.id());
    }

    @Transactional
    public Map<String, Object> createCourse(UserPrincipal teacher, CourseCreateRequest request) {
        if (request.beginDate() != null && request.endDate() != null) {
            require(!request.endDate().isBefore(request.beginDate()), HttpStatus.BAD_REQUEST, "课程结束日期不能早于开始日期");
        }
        try {
            mapper.insertCourse(teacher.id(), request);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "课程代码已存在，请更换后重试");
        }
        long courseId = mapper.lastCourseId();
        auditService.record(teacher, "COURSE_CREATED", "COURSE", courseId,
                Map.of("name", request.name(), "term", request.term() == null ? "" : request.term()));
        return Map.of("id", courseId, "name", request.name(), "status", "ACTIVE");
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

    /** 系统班级库列表，供教师端按班级选择整班导入；只返回启用中的班级。 */
    public List<Map<String, Object>> importableClasses(UserPrincipal teacher, long courseId, String keyword) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        return mapper.importableClasses(courseId, keyword == null ? "" : keyword.trim());
    }

    /** 某班级的学生名册预览，enrolled 表示该学生已在当前课程中。 */
    public List<Map<String, Object>> classRoster(UserPrincipal teacher, long courseId, long classId) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        require(mapper.activeClass(classId) == 1, HttpStatus.NOT_FOUND, "班级不存在或已停用");
        return mapper.classRoster(classId, courseId);
    }

    @Transactional
    public StudentCourseUpdateResult importStudents(UserPrincipal teacher, long courseId, StudentImportRequest request) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        List<Long> classIds = new ArrayList<>(new LinkedHashSet<>(request.classIdsOrEmpty()));
        List<String> studentNos = new ArrayList<>(new LinkedHashSet<>(request.studentNosOrEmpty()));
        require(!classIds.isEmpty() || !studentNos.isEmpty(), HttpStatus.BAD_REQUEST,
                "请选择要导入的班级或填写学号");

        LinkedHashSet<Long> studentIds = new LinkedHashSet<>();
        for (Long classId : classIds) {
            require(classId != null && classId > 0 && mapper.activeClass(classId) == 1,
                    HttpStatus.BAD_REQUEST, "班级不存在或已停用：" + classId);
            List<Long> members = mapper.activeStudentIdsByClass(classId);
            if (members != null) studentIds.addAll(members);
        }

        int success = 0, skipped = 0;
        List<String> notFound = new ArrayList<>();
        for (String rawNo : studentNos) {
            String studentNo = rawNo == null ? "" : rawNo.trim();
            Long studentId = mapper.studentIdByNo(studentNo);
            if (studentId == null) { notFound.add(studentNo); continue; }
            studentIds.add(studentId);
        }
        require(studentIds.size() <= MAX_IMPORT_STUDENTS, HttpStatus.BAD_REQUEST,
                "单次最多导入 " + MAX_IMPORT_STUDENTS + " 名学生，请分班级分批导入");

        // 班级与学号可能指向同一人，按学生 ID 去重后统一处理；
        // 已在课程中的跳过，DROPPED 的重新激活，避免重复名单。
        for (Long studentId : studentIds) {
            String status = mapper.studentCourseStatus(studentId, courseId);
            if ("ACTIVE".equals(status)) { skipped++; continue; }
            mapper.importStudent(studentId, courseId); success++;
        }
        auditService.record(teacher, "STUDENTS_IMPORTED", "COURSE", courseId,
                Map.of("success", success, "skipped", skipped, "notFound", notFound.size(),
                        "classIds", classIds.toString(), "studentNos", studentNos.size()));
        return new StudentCourseUpdateResult(success, skipped, notFound);
    }

    @Transactional
    public void dropStudent(UserPrincipal teacher, long courseId, long studentId) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        require(mapper.dropStudent(studentId, courseId) == 1, HttpStatus.CONFLICT, "学生不在当前课程中或已经被移除");
        auditService.record(teacher, "STUDENT_DROPPED", "COURSE", courseId, Map.of("studentId", studentId));
    }

    /** 批量移除：去重后一次性 UPDATE，返回移除条数与被跳过的条数（已不在课程中的）。 */
    @Transactional
    public Map<String, Object> dropStudents(UserPrincipal teacher, long courseId, List<Long> studentIds) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long id : studentIds) {
            if (id != null) ids.add(id);
        }
        require(!ids.isEmpty(), HttpStatus.BAD_REQUEST, "请先选择要移除的学生");
        int removed = mapper.dropStudents(courseId, new ArrayList<>(ids));
        auditService.record(teacher, "STUDENTS_DROPPED", "COURSE", courseId,
                Map.of("requested", ids.size(), "removed", removed));
        return Map.of("requested", ids.size(), "removed", removed, "skipped", ids.size() - removed);
    }

    @Transactional
    public Map<String, Object> createAssignment(UserPrincipal teacher, long courseId, AssignmentCreateRequest request) {
        require(mapper.ownsCourse(courseId, teacher.id()) == 1, HttpStatus.NOT_FOUND, "课程不存在或不属于当前教师");
        validateBranches(request.branches());
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

    private void validateBranches(List<AssignmentCreateRequest.BranchRequest> branches) {
        double sum = branches.stream().mapToDouble(AssignmentCreateRequest.BranchRequest::weight).sum();
        require(Math.abs(sum - 1.0) <= EPSILON, HttpStatus.BAD_REQUEST, "评价分支权重之和必须为1");
        long distinctNames = branches.stream().map(b -> b.name().trim().toLowerCase()).distinct().count();
        require(distinctNames == branches.size(), HttpStatus.BAD_REQUEST, "评价分支名称不能重复");
    }

    public Map<String, Object> teacherAssignmentDetail(UserPrincipal teacher, long assignmentId) {
        require(mapper.ownsAssignment(assignmentId, teacher.id()) == 1, HttpStatus.NOT_FOUND,
                "作业不存在或不属于当前教师");
        Map<String, Object> detail = mapper.teacherAssignmentDetail(assignmentId, teacher.id());
        require(detail != null, HttpStatus.NOT_FOUND, "作业不存在或不属于当前教师");
        Map<String, Object> response = new LinkedHashMap<>(detail);
        response.put("parts", mapper.assignmentParts(assignmentId));
        response.put("partsLocked", mapper.assignmentDependencyCount(assignmentId) > 0);
        response.put("scorePublished", mapper.assignmentPublishedScoreCount(assignmentId) > 0);
        return response;
    }

    @Transactional
    public void updateAssignment(UserPrincipal teacher, long assignmentId, AssignmentUpdateRequest request) {
        require(mapper.ownsAssignment(assignmentId, teacher.id()) == 1, HttpStatus.NOT_FOUND,
                "作业不存在或不属于当前教师");
        Map<String, Object> current = mapper.teacherAssignmentDetail(assignmentId, teacher.id());
        require(current != null && !"ARCHIVED".equals(String.valueOf(current.get("status"))),
                HttpStatus.CONFLICT, "作业已归档，不能修改");
        validateBranches(request.branches());

        // W/Q 矩阵、标准人数和总分都参与已算出的贡献与成绩，一旦产生下游数据就不能改
        boolean partsLocked = mapper.assignmentDependencyCount(assignmentId) > 0;
        if (partsLocked) {
            require(branchesUnchanged(mapper.assignmentParts(assignmentId), request.branches()),
                    HttpStatus.CONFLICT, "作业已有团队、工作量、提交或评分记录，不能修改评价分支");
            require(current.get("standardGroupSize") instanceof Number number
                            && number.intValue() == request.standardGroupSize(),
                    HttpStatus.CONFLICT, "作业已有团队、工作量、提交或评分记录，不能修改标准团队人数");
        }
        if (mapper.assignmentPublishedScoreCount(assignmentId) > 0) {
            require(current.get("totalScore") instanceof Number number
                            && Math.abs(number.doubleValue() - request.totalScore()) <= EPSILON,
                    HttpStatus.CONFLICT, "成绩已发布，不能修改作业总分");
        }

        LocalDateTime existingDeadline = mapper.assignmentDeadlineForTeacher(assignmentId, teacher.id());
        boolean deadlineChanged = existingDeadline == null
                || !request.deadline().withNano(0).equals(existingDeadline.withNano(0));
        if (deadlineChanged) {
            require(request.deadline().isAfter(LocalDateTime.now()), HttpStatus.BAD_REQUEST,
                    "修改后的截止时间必须晚于当前时间");
        }

        require(mapper.updateAssignment(assignmentId, teacher.id(), request) == 1,
                HttpStatus.CONFLICT, "作业不存在或不属于当前教师");
        if (!partsLocked) {
            mapper.deleteAssignmentParts(assignmentId);
            for (int i = 0; i < request.branches().size(); i++) {
                var branch = request.branches().get(i);
                mapper.insertAssignmentPart(assignmentId, branch.name().trim(), branch.description(),
                        branch.weight(), i + 1);
            }
        }
        Object courseId = current.get("courseId");
        auditService.record(teacher, "ASSIGNMENT_UPDATED", "ASSIGNMENT", assignmentId, Map.of(
                "courseId", courseId == null ? "" : courseId,
                "partsLocked", partsLocked,
                "branchCount", request.branches().size()));
    }

    @Transactional
    public void archiveAssignment(UserPrincipal teacher, long assignmentId) {
        require(mapper.ownsAssignment(assignmentId, teacher.id()) == 1, HttpStatus.NOT_FOUND,
                "作业不存在或不属于当前教师");
        Map<String, Object> current = mapper.teacherAssignmentDetail(assignmentId, teacher.id());
        require(current != null && !"ARCHIVED".equals(String.valueOf(current.get("status"))),
                HttpStatus.CONFLICT, "作业已归档");
        require(mapper.archiveAssignment(assignmentId, teacher.id()) == 1,
                HttpStatus.CONFLICT, "作业不存在或不属于当前教师");
        Object courseId = current.get("courseId");
        Object groupCount = current.get("groupCount") == null ? 0 : current.get("groupCount");
        Object submissionCount = current.get("submissionCount") == null ? 0 : current.get("submissionCount");
        auditService.record(teacher, "ASSIGNMENT_ARCHIVED", "ASSIGNMENT", assignmentId,
                Map.of("courseId", courseId == null ? "" : courseId,
                        "groupCount", groupCount, "submissionCount", submissionCount));
    }

    private boolean branchesUnchanged(List<Map<String, Object>> existing,
                                      List<AssignmentCreateRequest.BranchRequest> requested) {
        if (existing.size() != requested.size()) return false;
        Map<String, Double> existingWeights = new LinkedHashMap<>();
        for (Map<String, Object> part : existing) {
            Object weight = part.get("weight");
            existingWeights.put(String.valueOf(part.get("name")).trim().toLowerCase(),
                    weight instanceof Number number ? number.doubleValue() : 0.0);
        }
        for (AssignmentCreateRequest.BranchRequest branch : requested) {
            Double value = existingWeights.get(branch.name().trim().toLowerCase());
            if (value == null || Math.abs(value - branch.weight()) > EPSILON) return false;
        }
        return true;
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
        mapper.lockAssignmentTeams(assignmentId);
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
        require(mapper.ownsAssignment(assignmentId, teacher.id()) == 1, HttpStatus.NOT_FOUND,
                "作业不存在或不属于当前教师");
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
        // Resolve attachments through the exact same group as the rest of the detail view,
        // never through a student's possibly stale/duplicate group memberships.
        detail.put("attachments", groupId == null ? List.of() : mapper.studentSubmissionAttachments(
                assignmentId, ((Number) groupId).longValue()));
        return detail;
    }

    /**
     * Registers an uploaded file as a team-visible draft attachment. Until the team submits,
     * only active group members can see/download it; teachers cannot see DRAFT submissions.
     */
    @Transactional
    public void registerDraftAttachment(UserPrincipal student, long assignmentId,
                                        DraftAttachmentRequest request) {
        Long groupId = mapper.studentGroup(assignmentId, student.id());
        require(groupId != null, HttpStatus.FORBIDDEN, "当前学生不属于该作业的有效团队");
        require("LEADER".equals(mapper.activeGroupMemberRole(groupId, student.id())),
                HttpStatus.FORBIDDEN, "只有小组组长可以上传作业附件");
        requireAssignmentOpen(assignmentId, "作业已截止，无法再登记小组附件");
        String normalized = request.storageKey().replace('\\', '/');
        require(normalized.matches("\\d{4}/\\d{2}/student/" + student.id()
                        + "/[0-9a-fA-F-]{36}\\.[A-Za-z0-9]{1,8}"),
                HttpStatus.BAD_REQUEST, "附件必须由当前学生账号上传");
        String fallback = normalized.substring(normalized.lastIndexOf('/') + 1);
        String name = AttachmentNames.sanitize(request.originalName(), fallback);
        long size = request.fileSize() == null ? 0 : Math.max(0L, request.fileSize());
        mapper.ensureDraftSubmission(assignmentId, groupId, student.id());
        Long submissionId = mapper.latestSubmissionId(assignmentId, groupId);
        require(submissionId != null, HttpStatus.CONFLICT, "草稿提交不可用");
        mapper.insertAttachment(submissionId, normalized, name, size);
    }

    /**
     * A team may remove any attachment of its current submission before grading: this covers
     * the first submission (DRAFT) and every re-submission (SUBMITTED/RETURNED) up to the
     * deadline. Graded, score-published and expired submissions stay frozen.
     */
    @Transactional
    public void deleteStudentAttachment(UserPrincipal student, long assignmentId, long attachmentId) {
        requireAssignmentOpen(assignmentId, "作业已截止，小组附件已锁定");
        Long groupId = mapper.studentGroup(assignmentId, student.id());
        require(groupId != null, HttpStatus.FORBIDDEN, "当前学生不属于该作业的有效团队");
        require("LEADER".equals(mapper.activeGroupMemberRole(groupId, student.id())),
                HttpStatus.FORBIDDEN, "只有小组组长可以移除作业附件");
        require(mapper.deleteStudentAttachment(attachmentId, assignmentId, groupId) == 1,
                HttpStatus.NOT_FOUND, "附件不存在、不属于本组，或作业已批阅/成绩已发布，不能删除");
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
        mapper.lockAssignmentTeams(assignmentId);
        requireAssignmentOpen(assignmentId, "作业已截止，小组已锁定，不能退出");
        Long groupId = mapper.studentGroup(assignmentId, student.id());
        require(groupId != null, HttpStatus.CONFLICT, "当前没有加入小组");
        requireGroupNotGraded(assignmentId, groupId);
        String role = mapper.activeGroupMemberRole(groupId, student.id());
        require(mapper.leaveStudentGroup(groupId, student.id()) == 1, HttpStatus.CONFLICT, "退出小组失败");
        if (mapper.activeGroupMemberCount(groupId) == 0) {
            mapper.dissolveEmptyGroup(groupId);
        } else {
            if ("LEADER".equals(role)) {
                Long nextLeader = mapper.nextGroupLeader(groupId);
                require(nextLeader != null && mapper.promoteGroupLeader(groupId, nextLeader) == 1,
                        HttpStatus.CONFLICT, "小组队长转让失败");
                mapper.updateGroupLeader(groupId, nextLeader);
            }
            mapper.syncGroupMemberCount(groupId);
        }
        invalidateGroupWorkloads(student, assignmentId, groupId);
    }

    @Transactional
    public Map<String,Object> createStudentGroup(UserPrincipal student, long assignmentId) {
        mapper.lockAssignmentTeams(assignmentId);
        Long courseId = mapper.assignmentCourseForStudent(assignmentId, student.id());
        Integer standard = mapper.studentAssignmentGroupSize(assignmentId);
        require(courseId != null && standard != null, HttpStatus.NOT_FOUND, "作业不存在或未发布");
        requireAssignmentOpen(assignmentId, "作业已截止，无法再创建小组");
        require(mapper.studentGroup(assignmentId, student.id()) == null, HttpStatus.CONFLICT, "你已经加入该作业的小组");
        int max = standard + 1;
        Map<String,Object> reusable = mapper.reusableStudentGroup(assignmentId);
        if (reusable != null) {
            long groupId = ((Number) reusable.get("id")).longValue();
            require(mapper.reactivateStudentGroup(groupId, student.id(), max) == 1,
                    HttpStatus.CONFLICT, "小组已被其他同学恢复，请重试");
            mapper.upsertStudentLeader(groupId, assignmentId, student.id());
            return Map.of("id", groupId, "name", reusable.get("name"), "maxMembers", max);
        }
        int no = mapper.nextGroupNo(assignmentId);
        mapper.insertStudentGroup("第" + no + "组", courseId, assignmentId, student.id(), max);
        long groupId = mapper.lastGroupId(); mapper.insertStudentLeader(groupId, assignmentId, student.id());
        return Map.of("id", groupId, "name", "第" + no + "组", "maxMembers", max);
    }

    @Transactional
    public void joinStudentGroup(UserPrincipal student, long assignmentId, int groupNo) {
        mapper.lockAssignmentTeams(assignmentId);
        Long courseId = mapper.assignmentCourseForStudent(assignmentId, student.id());
        require(courseId != null, HttpStatus.NOT_FOUND, "作业不存在或未选修该课程");
        requireAssignmentOpen(assignmentId, "作业已截止，无法再加入小组");
        Map<String,Object> group = mapper.findStudentGroup(assignmentId, "第" + groupNo + "组");
        require(group != null, HttpStatus.NOT_FOUND, "小组不存在");
        require(mapper.studentGroup(assignmentId, student.id()) == null, HttpStatus.CONFLICT, "你已经加入该作业的小组");
        require(mapper.activeGroupMemberCount(((Number) group.get("id")).longValue()) < ((Number) group.get("maxMembers")).intValue(), HttpStatus.CONFLICT, "小组人数已满");
        long groupId = ((Number) group.get("id")).longValue();
        requireGroupNotGraded(assignmentId, groupId);
        if (mapper.reactivateStudentGroupMember(groupId, assignmentId, student.id()) == 0) {
            mapper.joinStudentGroup(groupId, assignmentId, student.id());
        }
        mapper.syncGroupMemberCount(groupId);
        invalidateGroupWorkloads(student, assignmentId, groupId);
    }

    private void requireGroupNotGraded(long assignmentId, long groupId) {
        require(mapper.gradedGroupSubmission(assignmentId, groupId) == 0, HttpStatus.CONFLICT,
                "该小组已批改或发布成绩，不能变更成员");
    }

    private void invalidateGroupWorkloads(UserPrincipal student, long assignmentId, long groupId) {
        if (mapper.supersedeGroupWorkloads(assignmentId, groupId) > 0) {
            auditService.record(student, "WORKLOAD_SUPERSEDED_BY_ROSTER", "GROUP", groupId,
                    Map.of("assignmentId", assignmentId));
        }
    }

    public List<Map<String, Object>> studentGroupMembers(UserPrincipal student, long groupId) {
        require(mapper.studentOwnsGroup(groupId, student.id()) == 1, HttpStatus.NOT_FOUND,
                "团队不存在或当前学生不是有效成员");
        return mapper.groupMembers(groupId);
    }

    @Transactional
    public void submit(UserPrincipal student, long assignmentId, StudentSubmissionRequest request) {
        mapper.lockAssignmentTeams(assignmentId);
        Long groupId = mapper.studentGroup(assignmentId, student.id());
        require(groupId != null, HttpStatus.FORBIDDEN, "当前学生不属于该作业的有效团队");
        require(mapper.assignmentDeadline(assignmentId) != null, HttpStatus.CONFLICT,
                "作业未发布或未设置截止时间");
        requireAssignmentOpen(assignmentId, "作业已截止，无法再提交");
        // Only the leader edits the group's submission content and attachments; teammates get
        // the shared, read-only result. Workload allocation is a separate leader-only endpoint.
        require("LEADER".equals(mapper.activeGroupMemberRole(groupId, student.id())),
                HttpStatus.FORBIDDEN, "只有小组组长可以编辑并提交作业");
        // Contribution matrix first: the leader saves it in its own step, but a team cannot hand
        // in the work before the allocation exists. Whatever superseded the version (member left,
        // version replaced) leaves no LOCKED row, so the group must save again before submitting.
        require(mapper.lockedWorkloadCount(assignmentId, groupId) > 0, HttpStatus.BAD_REQUEST,
                "请先在「小组分工」中填写并保存贡献度，再提交作业");
        // The "submit note" IS the submission content. Older clients only send note, so fold
        // it into content; the UI sends both with the same value. A filled note alone is a
        // valid submission - attachments are optional.
        StudentSubmissionRequest effective = withNoteAsContent(request);
        // Attachments already registered on the team's current submission count as content,
        // including SUBMITTED/RETURNED: an attachment-only team must be able to re-submit
        // (and to remove an attachment before re-submitting) without uploading everything again.
        Long currentSubmissionId = mapper.latestSubmissionId(assignmentId, groupId);
        boolean hasTeamAttachments = currentSubmissionId != null
                && mapper.attachmentCount(currentSubmissionId) > 0;
        require((effective.content() != null && !effective.content().isBlank())
                        || (effective.filePath() != null && !effective.filePath().isBlank())
                        || (effective.attachments() != null && !effective.attachments().isEmpty())
                        || hasTeamAttachments,
                HttpStatus.BAD_REQUEST, "请填写提交说明或上传附件");
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
        mapper.upsertSubmission(assignmentId, groupId, student.id(), effective);
        Long submissionId = mapper.latestSubmissionId(assignmentId, groupId);
        if (submissionId != null && effective.attachments() != null) {
            for (SubmissionAttachmentInput attachment : effective.attachments()) {
                if (attachment == null || attachment.storageKey() == null || attachment.storageKey().isBlank()) continue;
                String normalized = attachment.storageKey().replace('\\', '/');
                require(normalized.matches("\\d{4}/\\d{2}/student/" + student.id() + "/[0-9a-fA-F-]{36}\\.[A-Za-z0-9]{1,8}"), HttpStatus.BAD_REQUEST, "附件路径不合法");
                String fallback = normalized.substring(normalized.lastIndexOf('/') + 1);
                String name = AttachmentNames.sanitize(attachment.originalName(), fallback);
                long size = attachment.fileSize() == null ? 0 : Math.max(0L, attachment.fileSize());
                mapper.insertAttachment(submissionId, normalized, name, size);
            }
        }
        auditService.record(student, "SUBMISSION_SAVED", "ASSIGNMENT", assignmentId, Map.of("groupId", groupId));
    }

    /**
     * The old two-day team-formation window is gone. From publication until the deadline every
     * student action (grouping, workload allocation, attachments, submission) is allowed; at the
     * deadline the whole assignment freezes for students.
     */
    private void requireAssignmentOpen(long assignmentId, String closedMessage) {
        require(mapper.expiredAssignmentCount(assignmentId) == 0, HttpStatus.CONFLICT, closedMessage);
    }

    /**
     * There is only one editable field on the student form: the submit note. The legacy
     * {@code content} column is kept as its alias so teachers, exports and older clients all
     * read the same text. Older clients that only send {@code note} are upgraded here.
     */
    private StudentSubmissionRequest withNoteAsContent(StudentSubmissionRequest request) {
        if (request.content() != null && !request.content().isBlank()) return request;
        String note = request.note();
        if (note == null || note.isBlank()) return request;
        return new StudentSubmissionRequest(note, request.filePath(), note,
                request.attachments(), request.contributionScores());
    }

    private void require(boolean condition, HttpStatus status, String message) {
        if (!condition) throw new BusinessException(status, message);
    }
}
