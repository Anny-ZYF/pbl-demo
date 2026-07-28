package com.gdmu.teaching;

import jakarta.validation.constraints.Size;

public record StudentSubmissionRequest(@Size(max = 20000) String content,
                                       @Size(max = 500) String filePath,
                                       @Size(max = 1000) String note) { }
