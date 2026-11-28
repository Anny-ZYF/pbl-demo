package com.gdmu.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AccountImportService {
    // 表头既认中文（当前模板）也认英文（历史文件），列顺序不限。
    private static final Map<String, String> COLUMNS = columnAliases();
    private static final Map<String, String> COLUMN_LABELS = columnLabels();
    // 角色由导入入口的「学生 / 教师」选择决定，CSV 里不再要求 role 列。
    private static final List<String> REQUIRED_COLUMNS = List.of("name", "username", "department");
    private final AdminService adminService;
    private final AuditService auditService;
    private final AdminMapper mapper;
    private final ObjectMapper objectMapper;

    public AccountImportService(AdminService adminService, AuditService auditService,
                                AdminMapper mapper, ObjectMapper objectMapper) {
        this.adminService = adminService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public ImportReport importCsv(UserPrincipal admin, MultipartFile file) {
        return importCsv(admin, file, null);
    }

    /**
     * @param roleHint 导入入口已选定「学生 / 教师」时传进来的角色；老文件若仍带 role 列且填了值，
     *                 以行内值为准，避免同一份文件两种来源打架。
     */
    public ImportReport importCsv(UserPrincipal admin, MultipartFile file, String roleHint) {
        String filename = file.getOriginalFilename();
        if (file.isEmpty() || filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请选择 UTF-8 编码的 CSV 文件");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "账号导入文件不能超过2MB");
        }
        String batchNo = "IMPORT-" + UUID.randomUUID();
        mapper.insertImportBatch(batchNo, filename, fileSha256(file), admin.id());
        List<Map<String, Object>> successes = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int total = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String first = reader.readLine();
            if (first == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "CSV 文件为空");
            if (first.startsWith("\uFEFF")) first = first.substring(1);
            List<String> keys = readHeader(parseCsvLine(first));
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) continue;
                total++;
                if (total > 5000) throw new BusinessException(HttpStatus.BAD_REQUEST, "单次最多导入5000个账号");
                try {
                    List<String> cells = parseCsvLine(line);
                    if (cells.size() > keys.size()) {
                        throw new IllegalArgumentException("列数多于表头（表头 " + keys.size() + " 列，本行 "
                                + cells.size() + " 列）");
                    }
                    AccountCreateRequest request = accountRequest(toRow(keys, cells), roleHint);
                    successes.add(new LinkedHashMap<>(adminService.createAccount(admin, request)));
                } catch (RuntimeException ex) {
                    errors.add(new RowError(rowNumber, safeMessage(ex)));
                }
            }
        } catch (BusinessException ex) {
            failBatch(batchNo, total, successes.size(), errors, ex.getMessage());
            throw ex;
        } catch (IOException ex) {
            String message = "CSV 文件读取失败";
            failBatch(batchNo, total, successes.size(), errors, message);
            throw new BusinessException(HttpStatus.BAD_REQUEST, message);
        }
        mapper.completeImportBatch(batchNo, errors.isEmpty() ? "COMPLETED" : "PARTIAL", total,
                successes.size(), errors.size(), json(errors));
        auditService.record(admin, "ACCOUNT_IMPORT_COMPLETED", "IMPORT_BATCH", batchNo,
                Map.of("totalRows", total, "successRows", successes.size(), "failureRows", errors.size()));
        return new ImportReport(batchNo, total, successes.size(), errors.size(), successes, errors);
    }

    private void failBatch(String batchNo, int total, int success, List<RowError> errors, String message) {
        List<RowError> report = new ArrayList<>(errors);
        report.add(new RowError(0, message == null || message.isBlank() ? "导入失败" : message));
        mapper.completeImportBatch(batchNo, success == 0 ? "FAILED" : "PARTIAL", total,
                success, Math.max(total - success, 1), json(report));
    }

    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("引号未闭合");
        cells.add(current.toString().trim());
        return cells;
    }

    private List<String> readHeader(List<String> cells) {
        List<String> keys = new ArrayList<>();
        for (String cell : cells) {
            String label = cell.trim();
            String key = COLUMNS.get(label);
            if (key == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "无法识别的表头列：" + label
                        + "；可用列：" + String.join("、", COLUMN_LABELS.values()));
            }
            if (keys.contains(key)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "CSV 表头存在重复列：" + label);
            }
            keys.add(key);
        }
        for (String required : REQUIRED_COLUMNS) {
            if (!keys.contains(required)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,
                        "CSV 表头缺少「" + COLUMN_LABELS.get(required) + "」列");
            }
        }
        return keys;
    }

    private Map<String, String> toRow(List<String> keys, List<String> cells) {
        Map<String, String> row = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            row.put(keys.get(i), i < cells.size() ? cells.get(i).trim() : "");
        }
        return row;
    }

    private AccountCreateRequest accountRequest(Map<String, String> row, String roleHint) {
        String role = resolveRole(row.get("role"), roleHint);
        boolean student = "ROLE_STUDENT".equals(role);

        String departmentName = requiredName(row.get("department"), "所属学院");
        mapper.upsertDepartment(departmentName);
        Long departmentId = mapper.departmentIdByName(departmentName);
        if (departmentId == null) throw new IllegalStateException("所属学院创建失败");

        Long majorId = null;
        Long classId = null;
        if (student) {
            String majorName = requiredName(row.get("major"), "专业");
            mapper.upsertMajor(departmentId, majorName);
            majorId = mapper.majorIdByName(departmentId, majorName);
            if (majorId == null) throw new IllegalStateException("专业创建失败");

            String className = requiredName(row.get("class"), "班级");
            classId = mapper.classIdByName(departmentId, className);
            if (classId == null) {
                mapper.upsertClass(departmentId, className);
                classId = mapper.classIdByName(departmentId, className);
            }
            if (classId == null) throw new IllegalStateException("班级创建失败");
        } else if (!isBlank(row.get("major")) || !isBlank(row.get("class")) || !isBlank(row.get("grade"))) {
            throw new IllegalArgumentException("教师账号不能填写专业、班级或年级");
        }

        return new AccountCreateRequest(role, text(row.get("username")), text(row.get("name")),
                text(row.get("initialPassword")), departmentId, majorId, classId, nullableInteger(row.get("grade")));
    }

    private String resolveRole(String value, String fallback) {
        String raw = !isBlank(value) ? value.trim() : (fallback == null ? "" : fallback.trim());
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.equals("ROLE_TEACHER") || upper.equals("TEACHER") || raw.equals("教师") || raw.equals("教师账号")) {
            return "ROLE_TEACHER";
        }
        if (upper.equals("ROLE_STUDENT") || upper.equals("STUDENT") || raw.equals("学生") || raw.equals("学生账号")) {
            return "ROLE_STUDENT";
        }
        throw new IllegalArgumentException("角色必须为教师或学生");
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Map<String, String> columnAliases() {
        Map<String, String> map = new HashMap<>();
        map.put("姓名", "name");
        map.put("name", "name");
        map.put("displayName", "name");
        map.put("账号", "username");
        map.put("username", "username");
        map.put("学院", "department");
        map.put("department", "department");
        map.put("专业", "major");
        map.put("major", "major");
        map.put("班级", "class");
        map.put("class", "class");
        map.put("年级", "grade");
        map.put("grade", "grade");
        map.put("初始密码", "initialPassword");
        map.put("initialPassword", "initialPassword");
        map.put("角色", "role");
        map.put("role", "role");
        return Map.copyOf(map);
    }

    private static Map<String, String> columnLabels() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("name", "姓名");
        map.put("username", "账号");
        map.put("department", "学院");
        map.put("major", "专业");
        map.put("class", "班级");
        map.put("grade", "年级");
        map.put("initialPassword", "初始密码");
        map.put("role", "角色");
        // copyOf 会打乱迭代顺序，而错误提示里的「可用列」需要稳定顺序。
        return Collections.unmodifiableMap(map);
    }

    private String requiredName(String value, String label) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) throw new IllegalArgumentException(label + "不能为空");
        if (name.length() > 100) throw new IllegalArgumentException(label + "不能超过100个字符");
        return name;
    }

    private Integer nullableInteger(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "导入失败" : message.substring(0, Math.min(200, message.length()));
    }

    private String fileSha256(MultipartFile file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "无法校验导入文件");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record ImportReport(String batchNo, int totalRows, int successRows, int failureRows,
                               List<Map<String, Object>> createdAccounts, List<RowError> errors) { }
    public record RowError(int row, String message) { }
}
