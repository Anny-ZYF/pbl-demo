package com.gdmu.scoring;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record GradeSubmissionRequest(@DecimalMin("0") @DecimalMax("100") double groupScore,
                                     @Size(max = 2000) String comment) {
}
