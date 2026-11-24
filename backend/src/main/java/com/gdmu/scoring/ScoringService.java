package com.gdmu.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.UserPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScoringService {
    private static final double EPSILON = 1e-6;
    /** 发布即视为采用算法分，写进 score_result.review_reason 作为可追溯说明。 */
    private static final String AUTO_REVIEW_REASON = "发布时采用算法建议分（未人工调整）";
    private final ScoringMapper mapper;
    private final PaperScoringEngine engine;
    private final ModelClient modelClient;
    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalObjectMapper;
    private final AuditService auditService;

    public ScoringService(ScoringMapper mapper, PaperScoringEngine engine, ModelClient modelClient,
                          ObjectMapper objectMapper, AuditService auditService) {
        this.mapper = mapper;
        this.engine = engine;
        this.modelClient = modelClient;
        this.objectMapper = objectMapper;
        this.canonicalObjectMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.auditService = auditService;
    }

    /**
     * Records the team score and immediately runs the scoring rule so individual scores show up in
     * the score overview without an extra click. A scoring failure (no branches, no members, ...)
     * never rolls back the team score: the response carries {@code scoringSkipped} + the reason.
     */
    @Transactional
    public Map<String, Object> gradeSubmission(UserPrincipal teacher, long submissionId,
                                               GradeSubmissionRequest request) {
        require(mapper.gradeSubmission(submissionId, teacher.id(), request.groupScore(), request.comment()) == 1,
                HttpStatus.CONFLICT, "提交不存在、不属于当前教师或成绩已经发布");
        auditService.record(teacher, "GROUP_SCORE_RECORDED", "SUBMISSION", submissionId,
                Map.of("groupScore", request.groupScore()));
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            Map<String, Object> run = runScoring(teacher, submissionId);
            response.put("scoringSkipped", false);
            response.put("scoringRunId", run.get("id"));
            response.put("scoringStatus", run.get("status"));
            response.put("contributionSource", run.get("contributionSource"));
            response.put("memberCount", ((List<?>) run.get("results")).size());
        } catch (BusinessException ex) {
            // Scoring prerequisites are not met yet (for example the assignment has no branches).
            response.put("scoringSkipped", true);
            response.put("scoringMessage", ex.getMessage());
        }
        return response;
    }

    @Transactional
    public Map<String, Object> createWorkload(UserPrincipal teacher, long assignmentId, long groupId,
                                               WorkloadAllocationRequest request) {
        // Students are frozen at the deadline, but the teacher keeps an escape hatch: a group that
        // never submitted a workload version would otherwise be impossible to score. Any version
        // created after the deadline is locked by the same auto-lock path (sweeper + lazy read).
        mapper.lockAssignmentForWorkload(assignmentId);
        require(mapper.teacherOwnsGroup(groupId, assignmentId, teacher.id()) == 1, HttpStatus.NOT_FOUND,
                "团队不存在或不属于当前教师的作业");
        return persistWorkload(teacher, assignmentId, groupId, request, true);
    }

    @Transactional
    public Map<String, Object> proposeWorkload(UserPrincipal student, long assignmentId, long groupId,
                                               WorkloadAllocationRequest request) {
        mapper.lockAssignmentForWorkload(assignmentId);
        require(mapper.studentLeadsGroup(groupId, assignmentId, student.id()) == 1, HttpStatus.FORBIDDEN,
                "只有该团队组长可以提交工作量分配方案");
        requireWorkloadOpen(assignmentId);
        require(mapper.gradedGroupSubmission(assignmentId, groupId) == 0, HttpStatus.CONFLICT,
                "小组已批改，不能修改分工");
        return persistWorkload(student, assignmentId, groupId, request, false);
    }

    private Map<String, Object> persistWorkload(UserPrincipal actor, long assignmentId, long groupId,
                                                WorkloadAllocationRequest request, boolean createdByTeacher) {
        require(mapper.lockGroupForWorkload(groupId, assignmentId) != null, HttpStatus.NOT_FOUND,
                "团队不存在或不属于当前作业");
        List<PartRow> parts = mapper.parts(assignmentId);
        List<MemberRow> members = mapper.members(groupId);
        require(!parts.isEmpty() && !members.isEmpty(), HttpStatus.CONFLICT, "评价分支或团队成员为空");
        Set<Long> expectedParts = parts.stream().map(PartRow::id).collect(Collectors.toSet());
        Set<Long> requestParts = request.parts().stream().map(WorkloadAllocationRequest.PartAllocation::assignmentPartId)
                .collect(Collectors.toSet());
        require(request.parts().size() == expectedParts.size() && requestParts.equals(expectedParts),
                HttpStatus.BAD_REQUEST, "必须为全部评价分支提交工作量分配且不得重复");
        Set<Long> expectedMembers = members.stream().map(MemberRow::studentId).collect(Collectors.toSet());
        for (var part : request.parts()) {
            Set<Long> actualMembers = part.members().stream().map(WorkloadAllocationRequest.MemberShare::studentId)
                    .collect(Collectors.toSet());
            require(part.members().size() == expectedMembers.size() && actualMembers.equals(expectedMembers),
                    HttpStatus.BAD_REQUEST, "每个分支必须包含全部团队成员且不得重复");
            double sum = part.members().stream().mapToDouble(WorkloadAllocationRequest.MemberShare::share).sum();
            require(Math.abs(sum - 1.0) <= EPSILON, HttpStatus.BAD_REQUEST, "每个分支的工作量占比之和必须为1");
        }
        int version = mapper.nextWorkloadVersion(assignmentId, groupId);
        mapper.supersedeOpenWorkloads(groupId, assignmentId);
        if (createdByTeacher) mapper.insertWorkloadSet(groupId, assignmentId, version, actor.id());
        else mapper.insertStudentWorkloadSet(groupId, assignmentId, version, actor.id());
        long setId = mapper.workloadSetId(groupId, assignmentId, version);
        for (var part : request.parts()) {
            for (var member : part.members()) {
                mapper.insertWorkloadItem(setId, part.assignmentPartId(), member.studentId(), member.share(),
                        member.evidence() == null || member.evidence().isBlank() ? null
                                : json(Map.of("text", member.evidence())));
            }
        }
        // No member confirmation any more: an allocation version is locked as soon as it is saved.
        auditService.record(actor, "WORKLOAD_LOCKED", "WORKLOAD_SET", setId,
                Map.of("assignmentId", assignmentId, "groupId", groupId, "version", version));
        return Map.of("id", setId, "version", version, "status", "LOCKED");
    }

    @Transactional
    public Map<String, Object> studentWorkload(UserPrincipal student, long assignmentId) {
        autoLockExpired(assignmentId);
        Map<String, Object> summary = mapper.studentWorkload(assignmentId, student.id());
        if (summary == null) throw new BusinessException(HttpStatus.NOT_FOUND, "当前小组尚无分工方案");
        long setId = ((Number) summary.get("id")).longValue();
        Map<String, Object> response = new LinkedHashMap<>(summary);
        response.put("items", mapper.workloadItems(setId));
        response.put("objections", mapper.workloadObjections(setId));
        List<Map<String, Object>> confirmations = mapper.workloadConfirmations(setId);
        response.put("confirmations", confirmations);
        response.put("pendingMembers", pendingMembers(confirmations));
        return response;
    }

    /** Names of members who have not confirmed yet, so the UI can explain why a set is not locked. */
    private List<String> pendingMembers(List<Map<String, Object>> confirmations) {
        if (confirmations == null || confirmations.isEmpty()) return List.of();
        return confirmations.stream()
                .filter(row -> !"CONFIRMED".equals(String.valueOf(row.get("status"))))
                .map(row -> String.valueOf(row.get("studentName")))
                .toList();
    }

    // ---- Deadline window and auto-lock -------------------------------------

    /** There is no team-formation phase: the workload window is publication .. deadline. */
    private void requireWorkloadOpen(long assignmentId) {
        require(mapper.expiredAssignmentCount(assignmentId) == 0, HttpStatus.CONFLICT,
                "作业已截止，工作量分配已自动锁定");
    }

    private void requireConfirmationOpen(long setId) {
        require(mapper.expiredWorkloadSetCount(setId) == 0, HttpStatus.CONFLICT,
                "作业已截止，工作量分配已自动锁定，不能再确认或提出异议");
    }

    /**
     * At the deadline, freeze each group's newest still-open workload version even when some
     * members never confirmed. Older versions are superseded so scoring sees exactly one LOCKED
     * snapshot per group. Groups that never submitted a version are deliberately left alone:
     * the system does not invent contribution data for them.
     */
    public int autoLockExpired(long assignmentId) {
        if (mapper.expiredAssignmentCount(assignmentId) == 0) return 0;
        List<Long> setIds = mapper.expiredOpenWorkloadSetIds(assignmentId);
        int locked = 0;
        for (Long setId : setIds) {
            if (mapper.autoLockWorkloadSet(setId) == 1) {
                mapper.supersedeOtherWorkloadSets(setId);
                locked++;
            }
        }
        return locked;
    }

    @Transactional
    public int autoLockAllExpired() {
        int locked = 0;
        for (Long assignmentId : mapper.assignmentsWithExpiredOpenWorkloads()) {
            locked += autoLockExpired(assignmentId);
        }
        return locked;
    }

    @Transactional
    public Map<String, Object> teacherWorkload(UserPrincipal teacher, long assignmentId, long groupId) {
        autoLockExpired(assignmentId);
        Map<String, Object> summary = mapper.teacherWorkload(assignmentId, groupId, teacher.id());
        if (summary == null) throw new BusinessException(HttpStatus.NOT_FOUND, "当前团队尚无工作量分配版本");
        long setId = ((Number) summary.get("id")).longValue();
        Map<String, Object> response = new LinkedHashMap<>(summary);
        response.put("items", mapper.workloadItems(setId));
        response.put("confirmations", mapper.workloadConfirmations(setId));
        return response;
    }

    @Transactional
    public void confirmWorkload(UserPrincipal student, long setId, WorkloadConfirmationRequest request) {
        requireConfirmationOpen(setId);
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        require(status.equals("CONFIRMED") || status.equals("OBJECTED"), HttpStatus.BAD_REQUEST,
                "确认状态只能是 CONFIRMED 或 OBJECTED");
        require(!status.equals("OBJECTED") || (request.comment() != null && !request.comment().isBlank()),
                HttpStatus.BAD_REQUEST, "提出异议时必须填写原因");
        require(mapper.lockWorkloadSetForConfirmation(setId, student.id()) != null, HttpStatus.CONFLICT,
                "工作量分配不存在、已锁定或不属于当前学生");
        require(!status.equals("CONFIRMED") || mapper.objectedCount(setId) == 0, HttpStatus.CONFLICT,
                "该分工版本已有成员提出异议，需由组长重新提交后再确认");
        require(mapper.confirmWorkload(setId, student.id(), status, request.comment()) == 1,
                HttpStatus.CONFLICT, "工作量分配不存在、已锁定或不属于当前学生");
        if (status.equals("CONFIRMED") && mapper.unconfirmedCount(setId) == 0) {
            mapper.supersedePreviousLocked(setId);
            mapper.lockWorkload(setId);
        }
        auditService.record(student, status.equals("CONFIRMED") ? "WORKLOAD_CONFIRMED" : "WORKLOAD_OBJECTED",
                "WORKLOAD_SET", setId, Map.of("status", status));
    }

    @Transactional
    public Map<String, Object> runScoring(UserPrincipal teacher, long submissionId) {
        SubmissionContext context = mapper.submissionContext(submissionId, teacher.id());
        if (context == null) throw new BusinessException(HttpStatus.NOT_FOUND, "提交不存在或不属于当前教师");
        require(context.groupScore() != null, HttpStatus.CONFLICT, "请先录入团队作品分");
        require(context.scoringRuleId() != null, HttpStatus.CONFLICT, "没有可用的评分规则版本");
        // Deadline auto-lock is also applied lazily here, so scoring never depends on the
        // background sweeper having run first.
        autoLockExpired(context.assignmentId());
        Long workloadSetId = mapper.lockedWorkloadSet(context.assignmentId(), context.groupId());
        List<PartRow> parts = mapper.parts(context.assignmentId());
        List<MemberRow> members = mapper.members(context.groupId());
        require(!parts.isEmpty(), HttpStatus.CONFLICT, "作业还没有评价分支，无法生成个人成绩");
        require(!members.isEmpty(), HttpStatus.CONFLICT, "该小组没有有效成员，无法生成个人成绩");
        // No confirmation step any more, so a missing LOCKED version simply means the group never
        // saved an allocation. Instead of failing the whole run we fall back to an equal split so
        // the teacher still gets individual scores to review; the run is flagged as equal_split.
        boolean equalized = workloadSetId == null;
        List<AllocationRow> allocationRows = equalized ? List.of() : mapper.allocations(workloadSetId);
        List<PaperScoringEngine.Branch> branches = equalized
                ? equalBranches(parts, members)
                : buildBranches(parts, allocationRows, members);
        List<PaperScoringEngine.MemberContribution> contributions = engine.contributions(branches);
        String contributionSource = equalized ? "equal_split" : "workload";

        ActiveModelRow activeModel = mapper.activeModel();
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("assignmentId", context.assignmentId());
        inputSnapshot.put("groupId", context.groupId());
        inputSnapshot.put("standardGroupSize", context.standardGroupSize());
        inputSnapshot.put("groupScore", context.groupScore());
        inputSnapshot.put("contributionSource", contributionSource);
        inputSnapshot.put("parts", parts);
        inputSnapshot.put("allocations", allocationRows);
        String inputJson = canonicalJson(inputSnapshot);
        String inputHash = sha256(inputJson);
        String runNo = "RUN-" + UUID.randomUUID();
        try {
            mapper.insertRun(runNo, context, activeModel == null ? null : activeModel.id(), inputHash);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "相同输入与模型的评分运行已存在，请勿重复计算");
        }
        long runId = mapper.runId(runNo);

        List<ModelClient.ModelItemRequest> modelRequests = contributions.stream().map(c ->
                new ModelClient.ModelItemRequest(String.valueOf(c.studentId()), c.contribution(),
                        context.standardGroupSize(), context.groupScore())).toList();
        Optional<ModelClient.ModelBatchResponse> modelResponse = activeModel == null
                ? Optional.empty() : modelClient.predict(modelRequests).filter(response -> modelMatches(response, activeModel, contributions));
        Map<String, ModelClient.ModelItemResponse> predictions = modelResponse
                .map(response -> response.items().stream().collect(Collectors.toMap(ModelClient.ModelItemResponse::studentId,
                        Function.identity()))).orElseGet(Map::of);

        for (PaperScoringEngine.MemberContribution contribution : contributions) {
            PaperScoringEngine.Score ruleScore = engine.calculate(context.standardGroupSize(),
                    context.groupScore(), contribution.contribution());
            ModelClient.ModelItemResponse prediction = predictions.get(String.valueOf(contribution.studentId()));
            Double modelScore = prediction == null ? null : validScore(prediction.modelScore());
            Double delta = modelScore == null ? null : round2(Math.abs(modelScore - ruleScore.roundedScore()));
            boolean anomaly = delta != null && delta > 2.0;
            double suggestion = Math.max(PaperScoringEngine.MIN_SCORE, Math.min(PaperScoringEngine.MAX_SCORE,
                    modelScore == null ? ruleScore.roundedScore() : modelScore));
            String mode = prediction == null ? "rule_fallback" : "model";
            Map<String, Object> featureMap = new LinkedHashMap<>();
            featureMap.put("contribution", contribution.contribution());
            featureMap.put("standardGroupSize", context.standardGroupSize());
            featureMap.put("groupScore", context.groupScore());
            String featureJson = canonicalJson(featureMap);
            mapper.insertSnapshot(runId, contribution.studentId(), contribution.contribution(),
                    context.standardGroupSize(), context.groupScore(),
                    featureJson, inputJson, sha256(featureJson));

            Map<String, Object> explanation = new LinkedHashMap<>();
            explanation.put("ruleVersion", ruleScore.ruleVersion());
            explanation.put("branch", ruleScore.branch());
            explanation.put("threshold", round6(ruleScore.threshold()));
            explanation.put("contribution", round6(contribution.contribution()));
            explanation.put("normalizedContribution", round6(ruleScore.normalizedContribution()));
            explanation.put("boostCap", PaperScoringEngine.BOOST_CAP);
            explanation.put("scoreRange", List.of(PaperScoringEngine.MIN_SCORE, PaperScoringEngine.MAX_SCORE));
            explanation.put("mode", mode);
            explanation.put("modelExplanation", prediction == null ? null : safeExplanation(prediction.explanation()));
            explanation.put("contributionSource", contributionSource);
            explanation.put("assumption", "smoothstep rule (1108.py): threshold=1/p, B=20, score clipped to [0,100]");
            mapper.insertResult(runId, contribution.studentId(), contribution.contribution(), context.groupScore(),
                    ruleScore.roundedScore(), modelScore, delta, anomaly, suggestion, json(explanation));
        }
        String status = modelResponse.isEmpty() ? "DEGRADED" : "MODEL_COMPLETE";
        String error = status.equals("DEGRADED") ? "模型未激活、不可用或版本不匹配，已采用确定性规则结果" : null;
        mapper.completeRun(runId, status, error);
        auditService.record(teacher, "SCORING_RUN_COMPLETED", "SCORING_RUN", runId,
                Map.of("runNo", runNo, "status", status, "memberCount", contributions.size(),
                        "inputHash", inputHash, "contributionSource", contributionSource));
        Map<String, Object> response = new LinkedHashMap<>(runDetails(teacher, runId));
        response.put("contributionSource", contributionSource);
        return response;
    }

    /**
     * Equal split used when a group never saved a workload allocation: every branch gives all
     * members the same share, so the rule still produces an explainable individual score.
     */
    private List<PaperScoringEngine.Branch> equalBranches(List<PartRow> parts, List<MemberRow> members) {
        double share = 1.0 / members.size();
        List<PaperScoringEngine.MemberShare> shares = members.stream()
                .map(member -> new PaperScoringEngine.MemberShare(member.studentId(), share)).toList();
        return parts.stream()
                .map(part -> new PaperScoringEngine.Branch(part.id(), part.weight(), shares)).toList();
    }

    public Map<String, Object> runDetails(UserPrincipal teacher, long runId) {
        Map<String, Object> run = mapper.teacherRun(runId, teacher.id());
        if (run == null) throw new BusinessException(HttpStatus.NOT_FOUND, "评分运行不存在或不属于当前教师");
        Map<String, Object> response = new LinkedHashMap<>(run);
        response.put("results", mapper.runResults(runId));
        return response;
    }

    @Transactional
    public void publish(UserPrincipal teacher, long runId) {
        String status = mapper.lockTeacherRunStatus(runId, teacher.id());
        require(status != null, HttpStatus.NOT_FOUND, "评分运行不存在或不属于当前教师");
        // Repeated requests must not rewrite publication dates, projections or audit history.
        if (status.equals("PUBLISHED")) return;
        // 逐人复核流程已取消：算法分即最终分。发布时先把草稿结果统一标记为已复核，
        // 再走原来的 APPROVED -> PUBLISHED 状态机，保证成绩快照与审计口径不变。
        if (status.equals("MODEL_COMPLETE") || status.equals("DEGRADED")) {
            int autoReviewed = mapper.reviewAllResults(runId, teacher.id(), AUTO_REVIEW_REASON);
            require(autoReviewed > 0, HttpStatus.CONFLICT, "本次运行没有可发布的成绩");
            require(mapper.approveRun(runId, teacher.id()) == 1, HttpStatus.CONFLICT,
                    "评分运行状态已变化，请刷新后重试");
            auditService.record(teacher, "SCORING_RUN_AUTO_APPROVED", "SCORING_RUN", runId,
                    Map.of("resultCount", autoReviewed, "reason", AUTO_REVIEW_REASON));
            status = "APPROVED";
        }
        require(status.equals("APPROVED"), HttpStatus.CONFLICT, "当前评分运行状态不允许发布，请刷新后重试");
        int resultCount = mapper.runResults(runId).size();
        require(resultCount > 0 && mapper.unreviewedResultCount(runId) == 0, HttpStatus.CONFLICT,
                "本次运行必须包含成绩且全部完成复核");
        require(mapper.publishRun(runId, teacher.id()) == 1, HttpStatus.CONFLICT, "评分运行状态已变化，请刷新后重试");
        int published = mapper.publishResults(runId);
        require(published == resultCount, HttpStatus.CONFLICT, "成绩状态已变化，未发布任何成绩，请刷新后重试");
        mapper.markSubmissionPublished(runId);
        mapper.projectIndividualScores(runId);
        mapper.projectFinalScores(runId);
        auditService.record(teacher, "SCORES_PUBLISHED", "SCORING_RUN", runId, Map.of("resultCount", published));
    }

    public List<Map<String, Object>> studentScores(UserPrincipal student) {
        return mapper.publishedScores(student.id());
    }

    @Transactional
    public void appeal(UserPrincipal student, long resultId, AppealCreateRequest request) {
        PublishedScoreRow score = mapper.publishedScore(resultId, student.id());
        if (score == null) throw new BusinessException(HttpStatus.NOT_FOUND, "已发布成绩不存在或不属于当前学生");
        require(score.publishedAt() != null && !LocalDateTime.now().isAfter(score.publishedAt().plusDays(7)),
                HttpStatus.CONFLICT, "成绩申诉期限为发布后7天，当前已超期");
        require(mapper.openAppealCount(resultId, student.id()) == 0, HttpStatus.CONFLICT, "该成绩已有待处理申诉");
        String evidence = request.evidence() == null || request.evidence().isBlank() ? null
                : json(Map.of("text", request.evidence()));
        mapper.insertAppeal(resultId, student.id(), request.reason(), evidence);
        auditService.record(student, "SCORE_APPEAL_SUBMITTED", "SCORE_RESULT", resultId, Map.of());
    }

    public List<Map<String, Object>> teacherAppeals(UserPrincipal teacher) {
        return mapper.teacherAppeals(teacher.id());
    }

    public List<Map<String, Object>> studentAppeals(UserPrincipal student) {
        return mapper.studentAppeals(student.id());
    }

    @Transactional
    public void resolveAppeal(UserPrincipal teacher, long appealId, AppealResolveRequest request) {
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        require(status.equals("ACCEPTED") || status.equals("REJECTED"), HttpStatus.BAD_REQUEST,
                "申诉结论只能是 ACCEPTED 或 REJECTED");
        if (status.equals("ACCEPTED")) {
            require(request.adjustedScore() != null && Double.isFinite(request.adjustedScore())
                            && request.adjustedScore() >= PaperScoringEngine.MIN_SCORE
                            && request.adjustedScore() <= PaperScoringEngine.MAX_SCORE,
                    HttpStatus.BAD_REQUEST, "接受申诉时必须提供0到100之间的调整后成绩");
        }
        Double previousScore = mapper.effectiveScoreForAppeal(appealId, teacher.id());
        require(previousScore != null, HttpStatus.NOT_FOUND, "申诉不存在、不属于当前教师或已处理");
        if (status.equals("ACCEPTED")) {
            require(Math.abs(previousScore - request.adjustedScore()) > 0.005, HttpStatus.BAD_REQUEST,
                    "调整后成绩必须与当前成绩不同");
        }
        require(mapper.resolveAppeal(appealId, teacher.id(), status, request.resolution()) == 1,
                HttpStatus.NOT_FOUND, "申诉不存在、不属于当前教师或已处理");
        if (status.equals("ACCEPTED")) {
            require(mapper.insertScoreRevision(appealId, teacher.id(), previousScore,
                            request.adjustedScore(), request.resolution()) == 1,
                    HttpStatus.CONFLICT, "成绩修订写入失败");
            require(mapper.updateIndividualScoreProjection(appealId, request.adjustedScore()) == 1,
                    HttpStatus.CONFLICT, "个人成绩投影不存在");
            require(mapper.updateFinalScoreProjection(appealId, request.adjustedScore(), request.resolution()) == 1,
                    HttpStatus.CONFLICT, "最终成绩投影不存在");
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("status", status);
        auditDetail.put("previousScore", previousScore);
        auditDetail.put("revisedScore", request.adjustedScore());
        auditDetail.put("resolution", request.resolution());
        auditService.record(teacher, "SCORE_APPEAL_RESOLVED", "SCORE_APPEAL", appealId, auditDetail);
    }

    private List<PaperScoringEngine.Branch> buildBranches(List<PartRow> parts, List<AllocationRow> rows,
                                                           List<MemberRow> members) {
        Map<Long, List<AllocationRow>> byPart = rows.stream().collect(Collectors.groupingBy(AllocationRow::partId));
        Set<Long> expectedMembers = members.stream().map(MemberRow::studentId).collect(Collectors.toSet());
        return parts.stream().map(part -> {
            List<AllocationRow> partRows = byPart.getOrDefault(part.id(), List.of());
            Set<Long> actual = partRows.stream().map(AllocationRow::studentId).collect(Collectors.toSet());
            require(partRows.size() == expectedMembers.size() && actual.equals(expectedMembers), HttpStatus.CONFLICT,
                    "锁定的工作量快照不完整，无法评分");
            return new PaperScoringEngine.Branch(part.id(), part.weight(), partRows.stream()
                    .map(row -> new PaperScoringEngine.MemberShare(row.studentId(), row.share())).toList());
        }).toList();
    }

    private boolean modelMatches(ModelClient.ModelBatchResponse response, ActiveModelRow model,
                                 List<PaperScoringEngine.MemberContribution> contributions) {
        if (response == null || response.items() == null
                || !Objects.equals(response.ruleVersion(), PaperScoringEngine.RULE_VERSION)
                || !Objects.equals(response.modelVersion(), model.version())
                || model.artifactSha256() == null
                || response.artifactSha256() == null
                || !model.artifactSha256().equalsIgnoreCase(response.artifactSha256())) return false;
        Set<String> expected = contributions.stream().map(c -> String.valueOf(c.studentId())).collect(Collectors.toSet());
        Set<String> actual = response.items().stream().map(ModelClient.ModelItemResponse::studentId).collect(Collectors.toSet());
        return response.items().size() == expected.size() && actual.equals(expected)
                && response.items().stream().allMatch(item -> item.modelScore() != null
                && Double.isFinite(item.modelScore()) && item.modelScore() >= 0 && item.modelScore() <= 100);
    }

    private Double validScore(Double score) {
        return score != null && Double.isFinite(score) && score >= 0 && score <= 100 ? round2(score) : null;
    }

    private String safeExplanation(String explanation) {
        if (explanation == null) return null;
        return explanation.length() <= 1000 ? explanation : explanation.substring(0, 1000);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("评分快照无法序列化", ex);
        }
    }

    String canonicalJson(Object value) {
        try {
            return canonicalObjectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("评分快照无法规范化序列化", ex);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double round6(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    private void require(boolean condition, HttpStatus status, String message) {
        if (!condition) throw new BusinessException(status, message);
    }
}
