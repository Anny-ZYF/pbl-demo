package com.gdmu.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ModelRegistrationRequest(
        @NotBlank @Size(max = 64) String modelKey,
        @NotBlank @Size(max = 64) String version,
        @NotBlank @Size(max = 1024) String artifactUri,
        @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{64}$", message = "模型哈希必须为SHA-256") String artifactSha256,
        @NotBlank @Size(max = 10000) String featureSchemaJson,
        @NotBlank @Size(max = 20000) String hyperparametersJson,
        @NotBlank @Size(max = 50000) String metricsJson) {
}
