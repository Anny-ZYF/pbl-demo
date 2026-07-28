package com.gdmu.scoring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppealResolveRequest(@NotBlank String status,
                                   @NotBlank @Size(max = 2000) String resolution,
                                   Double adjustedScore) { }
