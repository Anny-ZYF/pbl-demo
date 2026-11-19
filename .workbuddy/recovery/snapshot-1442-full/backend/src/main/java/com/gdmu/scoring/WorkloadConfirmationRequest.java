package com.gdmu.scoring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkloadConfirmationRequest(@NotBlank String status, @Size(max = 1000) String comment) { }
