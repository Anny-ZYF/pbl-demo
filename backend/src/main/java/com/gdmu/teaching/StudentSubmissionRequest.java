package com.gdmu.teaching;

import jakarta.validation.constraints.Size;

public record StudentSubmissionRequest(@Size(max = 20000) String content,
                                       @Size(max = 5000) String filePath,
                                       @Size(max = 1000) String note,
                                       @Size(max = 20) java.util.List<@Size(max = 500) String> attachments,
                                       java.util.Map<Long, java.math.BigDecimal> contributionScores) { }
