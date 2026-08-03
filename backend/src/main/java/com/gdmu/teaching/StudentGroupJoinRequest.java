package com.gdmu.teaching;

import jakarta.validation.constraints.Min;

public record StudentGroupJoinRequest(@Min(1) int groupNo) { }
