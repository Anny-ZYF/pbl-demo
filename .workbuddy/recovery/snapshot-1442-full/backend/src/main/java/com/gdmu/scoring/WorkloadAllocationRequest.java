package com.gdmu.scoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WorkloadAllocationRequest(@NotEmpty List<@Valid PartAllocation> parts) {
    public record PartAllocation(long assignmentPartId, @NotEmpty List<@Valid MemberShare> members) { }
    public record MemberShare(long studentId,
            @DecimalMin("0") @DecimalMax("1") double share, String evidence) { }
}
