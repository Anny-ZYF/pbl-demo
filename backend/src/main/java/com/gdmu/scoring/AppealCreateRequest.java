package com.gdmu.scoring;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppealCreateRequest(@NotBlank @Size(max = 2000) String reason,
                                  @Size(max = 5000) String evidence) { }
