package com.gdmu.teaching;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public record AssignmentCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @NotNull @Future LocalDateTime deadline,
        @Min(1) @Max(4) int standardGroupSize,
        @DecimalMin("1") @DecimalMax("100") double totalScore,
        @NotEmpty List<@Valid BranchRequest> branches) {
    public record BranchRequest(@NotBlank @Size(max = 255) String name,
                                @Size(max = 2000) String description,
                                @DecimalMin("0") @DecimalMax("1") double weight) { }
}
