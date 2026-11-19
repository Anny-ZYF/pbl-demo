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
    private static final String MODEL_KEY = "smoothstep-score";
    private static final String FEATURES = "[\"标准人数\",\"个人贡献度\",\"作业总分\"]";
    private static final String ARTIFACT_URI = "algorithm-service://artifacts/score-model.joblib";

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
        ModelRegistrationRequest request = request(MODEL_KEY, FEATURES,
                "{\"status\":\"ELIGIBLE\",\"eligibility\":{\"passed\":true},"
                        + "\"holdout\":{\"rmse\":0.894273,\"mae\":0.638128,\"r2\":0.999209}}"
        );
        when(mapper.modelId(request.modelKey(), request.version())).thenReturn(8L);

        assertThat(service.registerModel(admin, request)).containsEntry("status", "VALIDATED");

        verify(mapper).insertModel(eq(request), eq(1L));
        verify(auditService).record(eq(admin), eq("MODEL_REGISTERED"), eq("MODEL_VERSION"), eq(8L), any());
    }

    @Test
    void rejectsWrongModelKeyOrFeatureOrderBeforePersistence() {
        assertThatThrownBy(() -> service.registerModel(admin, request("paper-score-p4", FEATURES, eligibleMetrics())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("模型键");
        assertThatThrownBy(() -> service.registerModel(admin, request(MODEL_KEY,
                "[\"个人贡献度\",\"标准人数\",\"作业总分\"]", eligibleMetrics())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("特征顺序");
        verify(mapper, never()).insertModel(any(), eq(1L));
    }

    @Test
    void rejectsLegacyFeatureContractWithActualGroupSize() {
        assertThatThrownBy(() -> service.registerModel(admin, request(MODEL_KEY,
                "[\"contribution\",\"actual_group_size\",\"group_score\"]", eligibleMetrics())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("特征顺序");
        verify(mapper, never()).insertModel(any(), eq(1L));
    }

    @Test
    void rejectsLegacyJsonArtifactUri() {
        assertThatThrownBy(() -> service.registerModel(admin,
                new ModelRegistrationRequest(MODEL_KEY, "xgb-p4-seed2025-test",
                        "algorithm-service://artifacts/xgboost-model.json", "a".repeat(64),
                        FEATURES, "{}", eligibleMetrics())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("artifactUri");
        verify(mapper, never()).insertModel(any(), eq(1L));
    }

    @Test
    void rejectsExplicitlyIneligibleOrNonsensicalMetrics() {
        assertThatThrownBy(() -> service.registerModel(admin, request(MODEL_KEY, FEATURES,
                "{\"status\":\"NOT_ELIGIBLE\",\"holdout\":{\"rmse\":-1,\"mae\":0,\"r2\":1}}")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("准入门槛");
        assertThatThrownBy(() -> service.registerModel(admin, request(MODEL_KEY, FEATURES,
                "{\"holdout\":{\"rmse\":2,\"mae\":1,\"r2\":0.9}}")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("准入门槛");
        verify(mapper, never()).insertModel(any(), eq(1L));
    }

    private ModelRegistrationRequest request(String key, String features, String metrics) {
        return new ModelRegistrationRequest(key, "smoothstep-xgb-a2b2c30a05b1",
                ARTIFACT_URI, "a".repeat(64), features, "{}", metrics);
    }

    private String eligibleMetrics() {
        return "{\"holdout\":{\"rmse\":0.5,\"mae\":0.4,\"r2\":0.999}}";
    }
}
