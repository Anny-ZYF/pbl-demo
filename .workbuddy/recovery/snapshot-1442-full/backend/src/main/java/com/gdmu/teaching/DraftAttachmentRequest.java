package com.gdmu.teaching;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Attachment registered while the team is still drafting the submission. */
public record DraftAttachmentRequest(@NotBlank @Size(max = 500) String storageKey,
                                     @Size(max = 255) String originalName,
                                     Long fileSize) { }
