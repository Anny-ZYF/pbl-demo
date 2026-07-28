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

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Validated
public class AdminService {
    private static final String PRODUCTION_MODEL_KEY = "paper-score-p4";
    private static final List<String> PRODUCTION_MODEL_FEATURES =
            List.of("contribution", "actual_group_size", "group_score");
    private final AdminMapper mapper;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

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
        Map<Long, List<Map<String, Object>>> classesByMajor = classes.stream().collect(java.util.stream.Collectors.groupingBy(
                row -> number(row.get("majorId")), LinkedHashMap::new, java.util.stream.Collectors.toList()));
        for (Map<String, Object> major : majors) {
            long majorId = ((Number) major.get("id")).longValue();
            major.put("classes", classesByMajor.getOrDefault(majorId, List.of()));
        }
        Map<Long, List<Map<String, Object>>> majorsByDepartment = majors.stream().collect(java.util.stream.Collectors.groupingBy(
                row -> number(row.get("departmentId")), LinkedHashMap::new, java.util.stream.Collectors.toList()));
        for (Map<String, Object> department : departments) {
            long departmentId = ((Number) department.get("id")).longValue();
            department.put("majors", majorsByDepartment.getOrDefault(departmentId, List.of()));
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
        String temporaryPassword = request.initialPassword() == null || request.initialPassword().isBlank()
                ? generatedPassword() : request.initialPassword();
        String hash = passwordEncoder.encode(temporaryPassword);
        try {
            switch (role) {
                case ADMIN -> mapper.insertAdmin(request.username().trim(), hash, request.displayName().trim());
                case TEACHER -> mapper.insertTeacher(request.username().trim(), hash, request.displayName().trim(), request.departmentId());
                case STUDENT -> mapper.insertStudent(request.username().trim(), hash, request.displayName().trim(),
                        request.majorId(), request.classId(), request.grade());
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(HttpStatus.CONFLICT, "账号已存在");
        }
        auditService.record(admin, "ACCOUNT_CREATED", "ACCOUNT", role + ":" + request.username(),
                Map.of("role", role.name(), "username", request.username()));
        return Map.of("username", request.username(), "role", role.name(), "temporaryPassword", temporaryPassword,
                "mustChangePassword", true);
    }

    @Transactional
    public void updateAccount(UserPrincipal admin, long id, AccountUpdateRequest request) {
        Role role = Role.parse(request.role());
        int changed = switch (role) {
            case ADMIN -> mapper.updateAdmin(id, request.displayName().trim());
            case TEACHER -> mapper.updateTeacher(id, request.displayName().trim(), request.departmentId());
            case STUDENT -> mapper.updateStudent(id, request.displayName().trim(), request.majorId(), request.classId(), request.grade());
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
        Map<String, Object> model = mapper.modelForActivation(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "模型版本不存在"));
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
        if (!request.artifactUri().matches("^algorithm-service://artifacts/[A-Za-z0-9._-]+\\.json$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "artifactUri 必须指向算法服务的 JSON 模型产物");
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
        if (!uri.matches("^algorithm-service://artifacts/[A-Za-z0-9._-]+\\.json$")
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

    private void requireOne(int changed, String message) {
        if (changed != 1) {
            throw new BusinessException(HttpStatus.NOT_FOUND, message);
        }
    }

    private String generatedPassword() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        return "Gdmu9-" + HexFormat.of().formatHex(bytes);
    }

    private record Page(int number, int size, int offset) { }
}
