package com.gdmu.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(@NotBlank String role, @JsonAlias("name") @NotBlank @Size(max = 50) String displayName,
                                   Long departmentId, Long majorId, Long classId, Integer grade) {
}
