package com.gdmu.common;

import java.nio.charset.StandardCharsets;

/**
 * 附件展示名的统一清洗。文件名会进入数据库和 Content-Disposition 响应头，
 * 必须去掉路径分隔符、控制字符和首尾点，避免头注入与跨平台路径问题。
 */
public final class AttachmentNames {
    private static final int MAX_LENGTH = 200;

    private AttachmentNames() {
    }

    public static String sanitize(String raw, String fallback) {
        String value = raw == null ? "" : raw.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[\\x00-\\x1F\\x7F]", "").replace("\"", "").replace(";", "").trim();
        value = value.replaceAll("^[.\\s]+|[.\\s]+$", "");
        if (value.isEmpty() || value.equals(".") || value.equals("..")) {
            value = fallback == null ? "" : fallback.replace('\\', '/');
            int fallbackSlash = value.lastIndexOf('/');
            if (fallbackSlash >= 0) {
                value = value.substring(fallbackSlash + 1);
            }
        }
        if (value.isEmpty()) {
            return "attachment";
        }
        if (value.length() > MAX_LENGTH) {
            int dot = value.lastIndexOf('.');
            String extension = dot > value.length() - 16 ? value.substring(dot) : "";
            value = value.substring(0, MAX_LENGTH - extension.length()) + extension;
        }
        return value;
    }

    /**
     * 旧版容器把 multipart 的 UTF-8 文件名按 ISO-8859-1 解码时会产生 mojibake。
     * 仅在出现典型 Latin-1 特征且能重新解出合法 UTF-8 时修复，避免误伤正常文件名。
     */
    public static String repairMojibake(String value) {
        if (value == null || value.isBlank() || !value.matches(".*[\\u0080-\\u00FF].*")) {
            return value;
        }
        byte[] bytes = value.getBytes(StandardCharsets.ISO_8859_1);
        String repaired = new String(bytes, StandardCharsets.UTF_8);
        return repaired.contains("\uFFFD") ? value : repaired;
    }
}
