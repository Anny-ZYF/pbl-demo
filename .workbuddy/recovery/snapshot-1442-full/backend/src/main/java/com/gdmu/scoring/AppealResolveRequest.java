package com.gdmu.scoring;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppealResolveRequest(@NotBlank String status,
                                   @NotBlank @Size(max = 2000) String resolution,
                                   @DecimalMin("0") @DecimalMax("100") Double adjustedScore) { }
