package com.gdmu.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.common.PageResponse;
import com.gdmu.security.AccountMapper;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Validated
public class AdminService {
    private static final String PRODUCTION_MODEL_KEY = "smoothstep-score";
    private static final List<String> PRODUCTION_MODEL_FEATURES =
            List.of("标准人数", "个人贡献度", "作业总分");
    private static final String ARTIFACT_URI_PATTERN =
            "^algorithm-service://artifacts/[A-Za-z0-9._-]+\\.joblib$";
    private final AdminMapper mapper;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    public AdminService(AdminMapper mapper, AccountMapper accountMapper, PasswordEncoder passwordEncoder,
                        AuditService auditService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> raw = mapper.dashboard();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("studentCount", raw.get("studentCount"));
        summary.put("teacherCount", raw.get("teacherCount"));
        summary.put("accountCount", number(raw.get("studentCount")) + number(raw.get("teacherCount")));
        summary.put("courseCount", raw.get("activeCourseCount"));
        summary.put("assignmentCount", raw.get("assignmentCount"));
        summary.put("scoringRunCount", raw.get("scoringRunCount"));
        summary.put("pendingAnomalyCount", raw.get("pendingAnomalyCount"));
        summary.put("openAppealCount", raw.get("openAppealCount"));
        summary.put("activeModelVersion", raw.get("activeModelVersion"));
        Map<String, Object> systemStatus = new LinkedHashMap<>();
        systemStatus.put("scoringService", number(raw.get("degradedRunCount")) > 0 ? "WARNING" : "HEALTHY");
        systemStatus.put("activeModelVersion", raw.get("activeModelVersion"));
        systemStatus.put("lastUpdatedAt", java.time.LocalDateTime.now());
        Map<String, Object> response = new LinkedHashMap<>(raw);
        response.put("summary", summary);
        response.put("systemStatus", systemStatus);
        response.put("recentScoringRuns", mapper.recentScoringRuns());
        response.put("updatedAt", java.time.LocalDateTime.now());
        return response;
    }

    public Map<String, Object> organizationOverview() {
        Map<String, Object> response = new LinkedHashMap<>(mapper.organizationOverview());
        List<Map<String, Object>> departments = mapper.departments().stream()
                .<Map<String, Object>>map(row -> new LinkedHashMap<>(row)).toList();
        List<Map<String, Object>> majors = mapper.majors().stream()
                .<Map<String, Object>>map(row -> new LinkedHashMap<>(row)).toList();
        List<Map<String, Object>> classes = mapper.classes().stream()
                .<Map<String, Object>>map(row -> new LinkedHashMap<>(row)).toList();
        Map<Long, List<Map<String, Object>>> classesByDepartment = classes.stream().collect(java.util.stream.Collectors.groupingBy(
                row -> number(row.get("departmentId")), LinkedHashMap::new, java.util.stream.Collectors.toList()));
        Map<Long, List<Map<String, Object>>> majorsByDepartment = majors.stream().collect(java.util.stream.Collectors.groupingBy(
                row -> number(row.get("departmentId")), LinkedHashMap::new, java.util.stream.Collectors.toList()));
        Map<Long, Long> teachersByDepartment = countBy(mapper.teacherCountsByDepartment(), "departmentId");
        Map<Long, Long> studentsByDepartment = countBy(mapper.studentCountsByDepartment(), "departmentId");
        Map<Long, Long> studentsByMajor = countBy(mapper.studentCountsByMajor(), "majorId");
        Map<Long, Long> studentsByClass = countBy(mapper.studentCountsByClass(), "classId");
        for (Map<String, Object> major : majors) {
            major.put("memberCount", studentsByMajor.getOrDefault(((Number) major.get("id")).longValue(), 0L));
        }
        for (Map<String, Object> clazz : classes) {
            clazz.put("memberCount", studentsByClass.getOrDefault(((Number) clazz.get("id")).longValue(), 0L));
        }
        for (Map<String, Object> department : departments) {
            long departmentId = ((Number) department.get("id")).longValue();
            department.put("memberCount", teachersByDepartment.getOrDefault(departmentId, 0L)
                    + studentsByDepartment.getOrDefault(departmentId, 0L));
            department.put("majors", majorsByDepartment.getOrDefault(departmentId, List.of()));
            department.put("classes", classesByDepartment.getOrDefault(departmentId, List.of()));
        }
        response.put("accountCount", number(response.get("activeTeacherCount")) + number(response.get("activeStudentCount")));
        response.put("departments", departments);
        return response;
    }

    public PageResponse<Map<String, Object>> accounts(String roleValue, String statusValue, String keyword,
                                                       int page, int pageSize) {
        String role = roleValue == null || roleValue.isBlank() ? null : Role.parse(roleValue).name();
        Integer status = parseStatus(statusValue);
        Page pageValue = page(page, pageSize);
        List<Map<String, Object>> rows = mapper.accounts(role, status, clean(keyword), pageValue.offset(), pageValue.size());
        return new PageResponse<>(rows, mapper.accountCount(role, status, clean(keyword)), pageValue.number(), pageValue.size());
    }

    @Transactional
    public Map<String, Object> createAccount(UserPrincipal admin, @Valid AccountCreateRequest request) {
        Role role = Role.parse(request.role());
        require(role == Role.TEACHER || role == Role.STUDENT, HttpStatus.BAD_REQUEST, "管理员只能创建教师或学生账号");
        String username = request.username().trim();
        // '#<id>' is reserved for tombstones of logically deleted accounts.
        require(!username.startsWith("#"), HttpStatus.BAD_REQUEST, "登录账号不能以 # 开头");
        require(mapper.accountUsernameCount(username) == 0, HttpStatus.CONFLICT, "登录账号已被使用");
        Long classId = validateOrganization(role, request.departmentId(), request.majorId(), request.classId(),
                request.classNumber(), request.grade());
        String temporaryPassword = request.initialPassword() == null || request.initialPassword().isBlank()
                ? generatedPassword(username) : request.initialPassword();
        String hash = passwordEncoder.encode(temporaryPassword);
        try {
            switch (role) {
                case TEACHER -> mapper.insertTeacher(username, hash, request.displayName().trim(), request.departmentId());
                case STUDENT -> mapper.insertStudent(username, hash, request.displayName().trim(),
                        request.majorId(), classId, request.grade());
                default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的账号角色");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "账号已存在");
        }
        auditService.record(admin, "ACCOUNT_CREATED", "ACCOUNT", role + ":" + username,
                Map.of("role", role.name(), "username", username));
        return Map.of("username", username, "role", role.name(), "temporaryPassword", temporaryPassword,
                "mustChangePassword", true);
    }

    @Transactional
    public void updateAccount(UserPrincipal admin, long id, AccountUpdateRequest request) {
        Role role = Role.parse(request.role());
        require(role == Role.TEACHER || role == Role.STUDENT, HttpStatus.BAD_REQUEST, "只允许维护教师或学生账号");
        Long classId = validateOrganization(role, request.departmentId(), request.majorId(), request.classId(),
                request.classNumber(), request.grade());
        int changed = switch (role) {
            case ADMIN -> mapper.updateAdmin(id, request.displayName().trim());
            case TEACHER -> mapper.updateTeacher(id, request.displayName().trim(), request.departmentId());
            case STUDENT -> mapper.updateStudent(id, request.displayName().trim(), request.majorId(), classId, request.grade());
        };
        requireOne(changed, "账号不存在");
        auditService.record(admin, "ACCOUNT_UPDATED", "ACCOUNT", role + ":" + id, Map.of("role", role.name()));
    }

    @Transactional
    public void setAccountStatus(UserPrincipal admin, long id, AccountStatusRequest request) {
        Role role = Role.parse(request.role());
        if (role == Role.ADMIN && id == admin.id() && request.status() == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "不能停用当前登录账号");
        }
        int changed = switch (role) {
            case ADMIN -> mapper.setAdminStatus(id, request.status());
            case TEACHER -> mapper.setTeacherStatus(id, request.status());
            case STUDENT -> mapper.setStudentStatus(id, request.status());
        };
        requireOne(changed, "账号不存在");
        if (request.status() == 0) {
            accountMapper.revokeAllRefreshTokens(id, role.name());
        }
        auditService.record(admin, request.status() == 1 ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED",
                "ACCOUNT", role + ":" + id, Map.of("status", request.status()));
    }

    @Transactional
    public void resetPassword(UserPrincipal admin, long id, ResetPasswordRequest request) {
        Role role = Role.parse(request.role());
        String hash = passwordEncoder.encode(request.newPassword());
        int changed = switch (role) {
            case ADMIN -> mapper.resetAdminPassword(id, hash);
            case TEACHER -> mapper.resetTeacherPassword(id, hash);
            case STUDENT -> mapper.resetStudentPassword(id, hash);
        };
        requireOne(changed, "账号不存在");
        accountMapper.revokeAllRefreshTokens(id, role.name());
        auditService.record(admin, "ACCOUNT_PASSWORD_RESET", "ACCOUNT", role + ":" + id, Map.of("role", role.name()));
    }

    /**
     * Logical deletion: keep the row (and all course / team / submission / score history),
     * but remove the account from admin lists, disable it, bump tokenVersion, revoke refresh
     * tokens and rewrite the login name to a reserved tombstone so it can be reused safely.
     */
    @Transactional
    public Map<String, Object> deleteAccount(UserPrincipal admin, long id, String roleValue) {
        Role role = Role.parse(roleValue);
        require(role == Role.TEACHER || role == Role.STUDENT,
                HttpStatus.BAD_REQUEST, "只能注销教师或学生账号");
        Map<String, Object> account = mapper.accountForDeletion(id, role.name());
        require(account != null && account.get("deletedAt") == null,
                HttpStatus.NOT_FOUND, "账号不存在或已注销");
        String username = String.valueOf(account.get("username"));
        String tombstone = "#" + id;
        require(tombstone.length() <= 20 && mapper.accountUsernameCount(tombstone) == 0,
                HttpStatus.CONFLICT, "注销标识已被占用，请稍后重试");
        int changed = switch (role) {
            case TEACHER -> mapper.softDeleteTeacher(id, tombstone, admin.id());
            case STUDENT -> mapper.softDeleteStudent(id, tombstone, admin.id());
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的账号角色");
        };
        requireOne(changed, "账号不存在或已注销");
        accountMapper.revokeAllRefreshTokens(id, role.name());
        auditService.record(admin, "ACCOUNT_DELETED", "ACCOUNT", role + ":" + id,
                Map.of("role", role.name(), "username", username, "tombstone", tombstone,
                        "mode", "LOGICAL_DELETE"));
        return Map.of("id", id, "role", role.name(), "username", username, "mode", "LOGICAL_DELETE");
    }

    /** Only organization nodes with no people and no course/team/submission references can go. */
    @Transactional
    public Map<String, Object> deleteOrganization(UserPrincipal admin, String type, long id) {
        OrgType orgType = OrgType.parse(type);
        Map<String, Object> node = switch (orgType) {
            case DEPARTMENT -> mapper.departmentById(id);
            case MAJOR -> mapper.majorById(id);
            case CLASS -> mapper.classById(id);
        };
        require(node != null, HttpStatus.NOT_FOUND, "组织节点不存在");
        Map<String, Object> usage = switch (orgType) {
            case DEPARTMENT -> mapper.departmentUsage(id);
            case MAJOR -> mapper.majorUsage(id);
            case CLASS -> mapper.classUsage(id);
        };
        List<String> blockers = new ArrayList<>();
        switch (orgType) {
            case DEPARTMENT -> {
                addBlocker(blockers, usage, "teachers", "名在职教师");
                addBlocker(blockers, usage, "majors", "个下属专业");
                addBlocker(blockers, usage, "classes", "个下属班级");
            }
            case MAJOR -> {
                addBlocker(blockers, usage, "students", "名在册学生");
                addBlocker(blockers, usage, "classes", "个下属班级");
                addBlocker(blockers, usage, "courses", "门关联课程");
                addBlocker(blockers, usage, "courseMajors", "条课程专业关联");
                addBlocker(blockers, usage, "groups", "个关联小组");
            }
            case CLASS -> {
                addBlocker(blockers, usage, "students", "名在册学生");
                addBlocker(blockers, usage, "groups", "个关联小组");
            }
        }
        require(blockers.isEmpty(), HttpStatus.CONFLICT,
                "该" + orgType.label + "仍有引用，不能删除：" + String.join("、", blockers));
        int changed = switch (orgType) {
            case DEPARTMENT -> mapper.deleteDepartment(id);
            case MAJOR -> mapper.deleteMajor(id);
            case CLASS -> mapper.deleteClass(id);
        };
        requireOne(changed, "组织节点不存在");
        String name = String.valueOf(node.get("name"));
        auditService.record(admin, "ORGANIZATION_DELETED", "ORGANIZATION", orgType.name() + ":" + id,
                Map.of("type", orgType.name(), "id", id, "name", name));
        return Map.of("id", id, "type", orgType.name(), "name", name);
    }

    private void addBlocker(List<String> blockers, Map<String, Object> usage, String key, String suffix) {
        long count = number(usage.get(key));
        if (count > 0) blockers.add(count + suffix);
    }

    private enum OrgType {
        DEPARTMENT("院系"), MAJOR("专业"), CLASS("班级");
        private final String label;

        OrgType(String label) {
            this.label = label;
        }

        static OrgType parse(String value) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "DEPARTMENT", "院系" -> DEPARTMENT;
                case "MAJOR", "专业" -> MAJOR;
                case "CLASS", "班级" -> CLASS;
                default -> throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "组织类型必须是 department、major 或 class");
            };
        }
    }

    public PageResponse<Map<String, Object>> courses(String keyword, String teacherName, String status,
                                                     int page, int pageSize) {
        Page p = page(page, pageSize);
        String normalizedStatus = clean(status);
        if (normalizedStatus != null && normalizedStatus.equalsIgnoreCase("OPEN")) normalizedStatus = "ACTIVE";
        if (normalizedStatus != null) normalizedStatus = normalizedStatus.toUpperCase();
        return new PageResponse<>(mapper.courses(clean(keyword), clean(teacherName), normalizedStatus,
                        p.offset(), p.size()),
                mapper.courseCount(clean(keyword), clean(teacherName), normalizedStatus), p.number(), p.size());
    }

    public PageResponse<Map<String, Object>> modelVersions(String status, int page, int pageSize) {
        Page p = page(page, pageSize);
        String normalized = clean(status) == null ? null : status.trim().toUpperCase();
        return new PageResponse<>(mapper.modelVersions(normalized, p.offset(), p.size()),
                mapper.modelVersionCount(normalized), p.number(), p.size());
    }

    @Transactional
    public Map<String, Object> registerModel(UserPrincipal admin, ModelRegistrationRequest request) {
        validateModelContract(request);
        validateMetrics(request.metricsJson());
        try {
            mapper.insertModel(request, admin.id());
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "模型键与版本已存在");
        }
        Long id = mapper.modelId(request.modelKey(), request.version());
        auditService.record(admin, "MODEL_REGISTERED", "MODEL_VERSION", id,
                Map.of("modelKey", request.modelKey(), "version", request.version(), "sha256", request.artifactSha256()));
        return Map.of("id", id, "status", "VALIDATED");
    }

    @Transactional
    public void activateModel(UserPrincipal admin, long id) {
        Map<String, Object> model = mapper.modelForActivation(id);
        if (model == null) throw new BusinessException(HttpStatus.NOT_FOUND, "模型版本不存在");
        String status = String.valueOf(model.get("status"));
        if (!("VALIDATED".equals(status) || "RETIRED".equals(status))) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有已验证或已停用模型可以激活");
        }
        String modelKey = String.valueOf(model.get("modelKey"));
        if (!PRODUCTION_MODEL_KEY.equals(modelKey) || !"XGBOOST".equals(String.valueOf(model.get("algorithm")))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "只能激活生产 XGBoost 评分模型");
        }
        validateStoredModelContract(model);
        mapper.retireActiveModel(modelKey);
        requireOne(mapper.activateModel(id), "模型激活失败");
        auditService.record(admin, "MODEL_ACTIVATED", "MODEL_VERSION", id, Map.of("modelKey", modelKey));
    }

    public PageResponse<Map<String, Object>> scoringRuns(String keyword, String status, int page, int pageSize) {
        Page p = page(page, pageSize);
        String normalized = clean(status) == null ? null : status.trim().toUpperCase();
        return new PageResponse<>(mapper.scoringRuns(clean(keyword), normalized, p.offset(), p.size()),
                mapper.scoringRunCount(clean(keyword), normalized), p.number(), p.size());
    }

    public PageResponse<Map<String, Object>> anomalies(String keyword, String severity, String status,
                                                       int page, int pageSize) {
        Page p = page(page, pageSize);
        String normalizedSeverity = clean(severity) == null ? null : severity.trim().toUpperCase();
        String normalizedStatus = clean(status) == null ? null : status.trim().toUpperCase();
        return new PageResponse<>(mapper.anomalies(clean(keyword), normalizedSeverity, normalizedStatus, p.offset(), p.size()),
                mapper.anomalyCount(clean(keyword), normalizedSeverity, normalizedStatus), p.number(), p.size());
    }

    @Transactional
    public void resolveAnomaly(UserPrincipal admin, long id, AnomalyResolutionRequest request) {
        requireOne(mapper.resolveAnomaly(id, admin.id(), request.resolution()), "异常不存在或已处理");
        auditService.record(admin, "SCORING_ANOMALY_REVIEWED", "SCORE_RESULT", id,
                Map.of("resolution", request.resolution()));
    }

    public PageResponse<Map<String, Object>> auditEvents(String eventType, String keyword, String actorRole,
                                                         String startDate, String endDate, int page, int pageSize) {
        Page p = page(page, pageSize);
        String role = clean(actorRole) == null ? null : Role.parse(actorRole).name();
        return new PageResponse<>(mapper.auditEvents(clean(eventType), clean(keyword), role, clean(startDate), clean(endDate),
                        p.offset(), p.size()),
                mapper.auditEventCount(clean(eventType), clean(keyword), role, clean(startDate), clean(endDate)),
                p.number(), p.size());
    }

    private void validateMetrics(String metricsJson) {
        try {
            JsonNode root = objectMapper.readTree(metricsJson);
            JsonNode metrics = root.has("holdout") ? root.path("holdout")
                    : root.has("test") ? root.path("test") : root;
            double rmse = metrics.path("rmse").asDouble(Double.POSITIVE_INFINITY);
            double mae = metrics.path("mae").asDouble(Double.POSITIVE_INFINITY);
            double r2 = metrics.path("r2").asDouble(Double.NEGATIVE_INFINITY);
            boolean explicitlyRejected = (root.has("status") && !"ELIGIBLE".equals(root.path("status").asText()))
                    || (root.path("eligibility").has("passed") && !root.path("eligibility").path("passed").asBoolean());
            if (explicitlyRejected || !Double.isFinite(rmse) || !Double.isFinite(mae) || !Double.isFinite(r2)
                    || rmse < 0 || mae < 0 || r2 > 1 || rmse > 1.0 || mae > 0.8 || r2 < 0.99) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "模型未达到准入门槛：RMSE≤1、MAE≤0.8、R²≥0.99");
            }
        } catch (JsonProcessingException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "metricsJson 不是合法 JSON");
        }
    }

    private void validateModelContract(ModelRegistrationRequest request) {
        if (!PRODUCTION_MODEL_KEY.equals(request.modelKey())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "模型键必须为 " + PRODUCTION_MODEL_KEY);
        }
        if (!request.artifactUri().matches(ARTIFACT_URI_PATTERN)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "artifactUri 必须指向算法服务的 joblib 模型产物");
        }
        try {
            JsonNode schema = objectMapper.readTree(request.featureSchemaJson());
            if (!schema.isArray() || schema.size() != PRODUCTION_MODEL_FEATURES.size()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "模型特征契约不匹配");
            }
            for (int i = 0; i < PRODUCTION_MODEL_FEATURES.size(); i++) {
                if (!PRODUCTION_MODEL_FEATURES.get(i).equals(schema.get(i).asText())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST,
                            "模型特征顺序必须为 " + PRODUCTION_MODEL_FEATURES);
                }
            }
            if (!objectMapper.readTree(request.hyperparametersJson()).isObject()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "hyperparametersJson 必须是 JSON 对象");
            }
        } catch (JsonProcessingException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "模型特征或超参数不是合法 JSON");
        }
    }

    private void validateStoredModelContract(Map<String, Object> model) {
        String uri = String.valueOf(model.get("artifactUri"));
        String hash = String.valueOf(model.get("artifactSha256"));
        String features = String.valueOf(model.get("featureSchemaJson"));
        String metrics = String.valueOf(model.get("metricsJson"));
        if (!uri.matches(ARTIFACT_URI_PATTERN)
                || !hash.matches("^[a-fA-F0-9]{64}$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "模型产物标识或哈希不合法");
        }
        validateModelContract(new ModelRegistrationRequest(PRODUCTION_MODEL_KEY, "stored", uri, hash,
                features, "{}", metrics));
        validateMetrics(metrics);
    }

    private Page page(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return new Page(safePage, safeSize, (safePage - 1) * safeSize);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase()) {
            case "1", "ACTIVE", "ENABLED" -> 1;
            case "0", "INACTIVE", "DISABLED" -> 0;
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "账号状态不合法");
        };
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Map<Long, Long> countBy(List<Map<String, Object>> rows, String keyColumn) {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyColumn);
            if (key instanceof Number id) counts.put(id.longValue(), number(row.get("memberCount")));
        }
        return counts;
    }

    private void requireOne(int changed, String message) {
        if (changed != 1) {
            throw new BusinessException(HttpStatus.NOT_FOUND, message);
        }
    }

    private void require(boolean condition, HttpStatus status, String message) {
        if (!condition) {
            throw new BusinessException(status, message);
        }
    }

    /**
     * Class numbers are numbered once per department; a student's major and
     * class are independent dimensions under the same department. Returns the
     * resolved class id so that the caller never writes an unverified id.
     */
    private Long validateOrganization(Role role, Long departmentId, Long majorId, Long classId,
                                      Integer classNumber, Integer grade) {
        require(departmentId != null, HttpStatus.BAD_REQUEST, role == Role.TEACHER ? "教师所属学院不能为空" : "学生所属学院不能为空");
        require(mapper.activeDepartment(departmentId) == 1, HttpStatus.BAD_REQUEST, "所属学院不存在或已停用");
        if (role == Role.TEACHER) {
            require(majorId == null && classId == null && classNumber == null && grade == null,
                    HttpStatus.BAD_REQUEST, "教师账号不能设置专业、班级或年级");
            return null;
        }
        require(majorId != null, HttpStatus.BAD_REQUEST, "学生专业不能为空");
        require(grade != null && grade >= 2000 && grade <= 2100, HttpStatus.BAD_REQUEST, "学生年级必须在2000至2100之间");
        require(mapper.activeMajorInDepartment(majorId, departmentId) == 1, HttpStatus.BAD_REQUEST, "专业不属于所选学院或已停用");
        return resolveClassId(departmentId, classId, classNumber);
    }

    /** Reuse an existing department-scoped class number, or create it on demand. */
    private Long resolveClassId(Long departmentId, Long classId, Integer classNumber) {
        require(classId != null || classNumber != null, HttpStatus.BAD_REQUEST, "学生班级不能为空");
        if (classNumber == null) {
            require(mapper.activeClassInDepartment(classId, departmentId) == 1, HttpStatus.BAD_REQUEST,
                    "班级不属于所选学院或已停用");
            return classId;
        }
        require(classNumber >= 1 && classNumber <= 9999, HttpStatus.BAD_REQUEST, "班级号必须是1至9999之间的整数");
        String name = String.valueOf(classNumber);
        Long resolved = mapper.classIdByName(departmentId, name);
        if (resolved == null) {
            mapper.upsertClass(departmentId, name);
            resolved = mapper.classIdByName(departmentId, name);
        }
        require(resolved != null, HttpStatus.CONFLICT, "班级创建失败，请稍后重试");
        return resolved;
    }

    private String generatedPassword(String username) {
        return "ABC" + username;
    }

    private record Page(int number, int size, int offset) { }
}
