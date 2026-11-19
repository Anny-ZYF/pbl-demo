package com.gdmu.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AccountStatusRequest(@NotBlank String role, @Min(0) @Max(1) int status) {
}
