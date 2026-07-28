package com.gdmu.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnomalyResolutionRequest(@NotBlank @Size(max = 500) String resolution) {
}
