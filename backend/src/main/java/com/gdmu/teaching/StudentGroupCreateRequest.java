package com.gdmu.teaching;

import jakarta.validation.constraints.NotNull;

public record StudentGroupCreateRequest(@NotNull Long assignmentId) { }
