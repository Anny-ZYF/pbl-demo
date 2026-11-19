package com.gdmu.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SubmissionAttachmentInputTest {
    private static final String KEY = "2026/11/student/9/7fb0c3f0-1b2a-4c3d-8e5f-6a7b8c9d0e1f.pdf";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyStringArrayIsStillAccepted() throws Exception {
        StudentSubmissionRequest request = mapper.readValue(
                "{\"content\":\"正文\",\"attachments\":[\"" + KEY + "\"]}", StudentSubmissionRequest.class);

        assertEquals(1, request.attachments().size());
        assertEquals(KEY, request.attachments().get(0).storageKey());
        assertNull(request.attachments().get(0).originalName());
        assertNull(request.attachments().get(0).fileSize());
    }

    @Test
    void objectArrayCarriesRealNameAndSize() throws Exception {
        String json = """
                {"attachments":[{"storageKey":"%s","originalName":"团队报告.pdf","fileSize":120045}]}
                """.formatted(KEY);
        StudentSubmissionRequest request = mapper.readValue(json, StudentSubmissionRequest.class);

        SubmissionAttachmentInput attachment = request.attachments().get(0);
        assertEquals(KEY, attachment.storageKey());
        assertEquals("团队报告.pdf", attachment.originalName());
        assertEquals(120045L, attachment.fileSize().longValue());
    }

    @Test
    void acceptsStoredObjectFieldAliases() throws Exception {
        String json = """
                {"attachments":[{"key":"%s","originalName":"report.pdf","size":42}]}
                """.formatted(KEY);
        SubmissionAttachmentInput attachment = mapper.readValue(json, StudentSubmissionRequest.class)
                .attachments().get(0);

        assertEquals(KEY, attachment.storageKey());
        assertEquals(42L, attachment.fileSize().longValue());
    }
}
