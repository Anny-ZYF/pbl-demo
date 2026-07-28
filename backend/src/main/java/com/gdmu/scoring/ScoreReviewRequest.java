package com.gdmu.scoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ScoreReviewRequest(@NotEmpty List<@Valid Decision> decisions) {
    public record Decision(long scoreResultId,
                           @NotNull @DecimalMin("0") @DecimalMax("100") Double finalScore,
                           @Size(max = 500) String reason) { }
}
