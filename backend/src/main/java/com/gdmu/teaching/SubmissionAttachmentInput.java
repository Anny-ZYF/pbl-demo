package com.gdmu.teaching;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.io.IOException;

/**
 * 提交作业时的附件描述。上传接口返回 storageKey、originalName、size，
 * 提交时必须一并带回，否则数据库只能存下 UUID 形态的存储文件名。
 *
 * 旧客户端只传字符串数组（storageKey），通过自定义反序列化保持兼容。
 */
@Schema(description = "作业附件；兼容旧客户端仅传 storageKey 字符串")
@JsonDeserialize(using = SubmissionAttachmentInput.Deserializer.class)
public record SubmissionAttachmentInput(
        @Schema(description = "上传接口返回的存储键") @Size(max = 500) String storageKey,
        @Schema(description = "原始文件名") @Size(max = 255) String originalName,
        @Schema(description = "文件字节数") Long fileSize) {

    public static SubmissionAttachmentInput ofKey(String key) {
        return new SubmissionAttachmentInput(key, null, null);
    }

    public static final class Deserializer extends JsonDeserializer<SubmissionAttachmentInput> {
        @Override
        public SubmissionAttachmentInput deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return ofKey(node.asText());
            }
            if (!node.isObject()) {
                return new SubmissionAttachmentInput(null, null, null);
            }
            JsonNode sizeNode = firstNonNull(node.get("fileSize"), node.get("size"));
            Long size = sizeNode != null && sizeNode.canConvertToLong() ? sizeNode.asLong() : null;
            return new SubmissionAttachmentInput(
                    text(firstNonNull(node.get("storageKey"), node.get("key"))),
                    text(node.get("originalName")),
                    size);
        }

        private static JsonNode firstNonNull(JsonNode primary, JsonNode fallback) {
            return primary != null && !primary.isNull() ? primary : fallback;
        }

        private static String text(JsonNode node) {
            return node != null && node.isValueNode() ? node.asText() : null;
        }
    }
}
