package com.jacolp.document.application.yjs;

import java.util.Objects;
import java.util.UUID;

/** Yjs 历史身份迁移中，一个重复资源引用的旧、新 {@code refId} 对。 */
public record YjsResourceReferenceRemap(String previousRefId, String refId) {

    /** 只接收可作为关系表键的 UUID，并统一为 Java UUID 的小写格式。 */
    public YjsResourceReferenceRemap {
        previousRefId = normalize(previousRefId, "previousRefId");
        refId = normalize(refId, "refId");
        if (previousRefId.equals(refId)) {
            throw new IllegalArgumentException("resource reference remap IDs must differ");
        }
    }

    private static String normalize(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a UUID", exception);
        }
    }
}
