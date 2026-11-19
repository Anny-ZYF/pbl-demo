package com.gdmu.teaching;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record StudentSubmissionRequest(@Size(max = 20000) String content,
                                       @Size(max = 5000) String filePath,
                                       @Size(max = 1000) String note,
                                       @Schema(description = "附件列表，元素可为 storageKey 字符串或含原始文件名的对象")
                                       @Size(max = 20) @Valid java.util.List<SubmissionAttachmentInput> attachments,
                                       java.util.Map<Long, java.math.BigDecimal> contributionScores) { }
