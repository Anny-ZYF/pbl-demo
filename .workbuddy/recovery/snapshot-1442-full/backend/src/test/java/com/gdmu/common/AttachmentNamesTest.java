package com.gdmu.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.ContentDisposition;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentNamesTest {

    @Test
    void stripsPathControlCharactersAndHeaderInjection() {
        assertEquals("报告.pdf", AttachmentNames.sanitize("C:\\tmp\\报告.pdf", "fallback.pdf"));
        assertEquals("报告.pdf", AttachmentNames.sanitize("报\"告;.pdf", "fallback.pdf"));
        assertEquals("报告.pdf", AttachmentNames.sanitize("报\r\n告.pdf", "fallback.pdf"));
    }

    @Test
    void fallsBackToStorageKeyFileNameWhenNameIsMissing() {
        assertEquals("uuid.pdf", AttachmentNames.sanitize("  ", "2026/11/student/63/uuid.pdf"));
        assertEquals("attachment", AttachmentNames.sanitize(null, null));
    }

    @Test
    void truncatesLongNamesButKeepsExtension() {
        String sanitized = AttachmentNames.sanitize("a".repeat(250) + ".docx", "fallback.docx");
        assertTrue(sanitized.length() <= 200);
        assertTrue(sanitized.endsWith(".docx"));
    }

    @Test
    void repairsUtf8FilenameMisreadAsLatin1() {
        String original = "团队报告-终稿.pdf";
        String mojibake = new String(original.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        assertEquals(original, AttachmentNames.repairMojibake(mojibake));
    }

    @Test
    void leavesNormalNamesAlone() {
        assertEquals("report.pdf", AttachmentNames.repairMojibake("report.pdf"));
        assertEquals("résumé.pdf", AttachmentNames.repairMojibake("résumé.pdf"));
        assertEquals("团队报告.pdf", AttachmentNames.repairMojibake("团队报告.pdf"));
    }

    @Test
    void downloadHeaderCarriesUtf8ChineseName() {
        String header = ContentDisposition.attachment()
                .filename("团队报告-终稿.docx", StandardCharsets.UTF_8).build().toString();
        assertTrue(header.contains("filename*=UTF-8''"), header);
        assertTrue(header.contains("%E5%9B%A2") || header.contains("%e5%9b%a2"), header);
    }
}
