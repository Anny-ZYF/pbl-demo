package com.gdmu.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.AccountMapper;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceModelValidationTest {
    @Mock AdminMapper mapper;
    @Mock AccountMapper accountMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    AdminService service;
    UserPrincipal admin;

    @BeforeEach
    void setUp() {
        service = new AdminService(mapper, accountMapper, passwordEncoder, auditService, new ObjectMapper());
        admin = new UserPrincipal(1, "admin", "管理员", Role.ADMIN, "hash", true, false, 0);
    }

    @Test
    void acceptsEligibleArtifactWithExactProductionFeatureContract() {
        ModelRegistrationRequest request = request("paper-score-p4",
                "[\"contribution\",\"actual_group_size\",\"group_score\"]",
                "{\"status\":\"ELIGIBLE\",\"eligibility\":{\"passed\":true},"
                        + "\"holdout\":{\"rmse\":0.482899,\"mae\":0.356837,\"r2\":0.999700}}"
        );
        when(mapper.modelId(request.modelKey(), request.version())).thenReturn(8L);

        assertThat(service.registerModel(admin, request)).containsEntry("status", "VALIDATED");

        verify(mapper).insertModel(eq(request), eq(1L));
        verify(auditService).record(eq(admin), eq("MODEL_REGISTERED"), eq("MODEL_VERSION"), eq(8L), any());
    }

    @Test
    void rejectsWrongModelKeyOrFeatureOrderBeforePersistence() {
        assertThatThrownBy(() -> service.registerModel(admin, request("other-model",
                "[\"contribution\",\"actual_group_size\",\"group_score\"]", eligibleMetrics())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("模型键");
        assertThatThrownBy(() -> service.registerModel(admin, request("paper-score-p4",
                "[\"group_score\",\"actual_group_size\",\"contribution\"]", eligibleMetrics())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("特征顺序");
        verify(mapper, never()).insertModel(any(), eq(1L));
    }

    @Test
    void rejectsExplicitlyIneligibleOrNonsensicalMetrics() {
        assertThatThrownBy(() -> service.registerModel(admin, request("paper-score-p4",
                "[\"contribution\",\"actual_group_size\",\"group_score\"]",
                "{\"status\":\"NOT_ELIGIBLE\",\"holdout\":{\"rmse\":-1,\"mae\":0,\"r2\":1}}")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("准入门槛");
    }

    private ModelRegistrationRequest request(String key, String features, String metrics) {
        return new ModelRegistrationRequest(key, "xgb-p4-seed2025-test",
                "algorithm-service://artifacts/xgboost-model.json", "a".repeat(64), features, "{}", metrics);
    }

    private String eligibleMetrics() {
        return "{\"holdout\":{\"rmse\":0.5,\"mae\":0.4,\"r2\":0.999}}";
    }
}
