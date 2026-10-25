package com.gdmu.teaching;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CourseCreateRequest(
        @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 32) String term,
        @Size(max = 4) String grade,
        LocalDate beginDate,
        LocalDate endDate) {
}
