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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AccountImportService {
    private static final List<String> HEADER = List.of("role", "username", "displayName", "departmentId",
            "majorId", "classId", "grade", "initialPassword");
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
            if (!parseCsvLine(first).equals(HEADER)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "CSV 表头必须为: " + String.join(",", HEADER));
            }
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) continue;
                total++;
                if (total > 5000) throw new BusinessException(HttpStatus.BAD_REQUEST, "单次最多导入5000个账号");
                try {
                    List<String> cells = parseCsvLine(line);
                    if (cells.size() != HEADER.size()) throw new IllegalArgumentException("列数不正确");
                    AccountCreateRequest request = new AccountCreateRequest(cells.get(0), cells.get(1), cells.get(2),
                            cells.get(7), nullableLong(cells.get(3)), nullableLong(cells.get(4)), nullableLong(cells.get(5)),
                            nullableInteger(cells.get(6)));
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

    private Long nullableLong(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value);
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
