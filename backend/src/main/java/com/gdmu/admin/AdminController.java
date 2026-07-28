package com.gdmu.admin;

import com.gdmu.common.ApiResponse;
import com.gdmu.common.PageResponse;
import com.gdmu.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService service;
    private final AccountImportService importService;

    public AdminController(AdminService service, AccountImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(service.dashboard());
    }

    @GetMapping("/organization/overview")
    public ApiResponse<Map<String, Object>> organizationOverview() {
        return ApiResponse.ok(service.organizationOverview());
    }

    @GetMapping("/accounts")
    public ApiResponse<PageResponse<Map<String, Object>>> accounts(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.accounts(role, status, keyword, page, pageSize));
    }

    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createAccount(
            @AuthenticationPrincipal UserPrincipal admin, @Valid @RequestBody AccountCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.createAccount(admin, request)));
    }

    @PostMapping(value = "/accounts/import", consumes = "multipart/form-data")
    public ApiResponse<AccountImportService.ImportReport> importAccounts(
            @AuthenticationPrincipal UserPrincipal admin,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return ApiResponse.ok(importService.importCsv(admin, file));
    }

    @PutMapping("/accounts/{id}")
    public ApiResponse<Void> updateAccount(@AuthenticationPrincipal UserPrincipal admin, @PathVariable long id,
                                           @Valid @RequestBody AccountUpdateRequest request) {
        service.updateAccount(admin, id, request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/accounts/{id}/status")
    public ApiResponse<Void> setAccountStatus(@AuthenticationPrincipal UserPrincipal admin, @PathVariable long id,
                                              @Valid @RequestBody AccountStatusRequest request) {
        service.setAccountStatus(admin, id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/accounts/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@AuthenticationPrincipal UserPrincipal admin, @PathVariable long id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(admin, id, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/courses")
    public ApiResponse<PageResponse<Map<String, Object>>> courses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.courses(keyword, teacherName, status, page, pageSize));
    }

    @GetMapping("/model-versions")
    public ApiResponse<PageResponse<Map<String, Object>>> modelVersions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.modelVersions(status, page, pageSize));
    }

    @PostMapping("/model-versions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerModel(
            @AuthenticationPrincipal UserPrincipal admin, @Valid @RequestBody ModelRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.registerModel(admin, request)));
    }

    @PostMapping("/model-versions/{id}/activate")
    public ApiResponse<Void> activateModel(@AuthenticationPrincipal UserPrincipal admin, @PathVariable long id) {
        service.activateModel(admin, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/scoring-runs")
    public ApiResponse<PageResponse<Map<String, Object>>> scoringRuns(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.scoringRuns(keyword, status, page, pageSize));
    }

    @GetMapping("/anomalies")
    public ApiResponse<PageResponse<Map<String, Object>>> anomalies(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.anomalies(keyword, severity, status, page, pageSize));
    }

    @PutMapping("/anomalies/{id}")
    public ApiResponse<Void> resolveAnomaly(@AuthenticationPrincipal UserPrincipal admin, @PathVariable long id,
                                            @Valid @RequestBody AnomalyResolutionRequest request) {
        service.resolveAnomaly(admin, id, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/audit-events")
    public ApiResponse<PageResponse<Map<String, Object>>> auditEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String actorRole,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.auditEvents(eventType, keyword, actorRole, startDate, endDate, page, pageSize));
    }
}
