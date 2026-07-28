package com.gdmu.teaching;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GroupCreateRequest(@NotBlank @Size(max = 100) String name,
                                 long leaderStudentId,
                                 @NotEmpty List<Long> memberStudentIds,
                                 @Size(max = 1000) String description) {
}
