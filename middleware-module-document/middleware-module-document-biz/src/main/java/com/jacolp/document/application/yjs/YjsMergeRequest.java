package com.jacolp.document.application.yjs;

import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** {@code POST /internal/yjs/merge} 使用的 JSON 请求报文。 */
record YjsMergeRequest(
        /** 可选的当前 Yjs 基础状态，使用 Base64 传输。<p>example: {@code AAECAw==}</p> */
        String baseState,
        /** 按接收顺序排列的 Yjs 增量列表，每项均为 Base64 字符串。<p>example: {@code [AQID, BAUG]}</p> */
        List<String> updates) {

    /** 固化更新列表，避免序列化前被调用方修改。 */
    YjsMergeRequest {
        updates = List.copyOf(Objects.requireNonNull(updates, "updates must not be null"));
    }

    /** 将可选基础状态和非空更新编码成服务间 Base64 报文。 */
    static YjsMergeRequest from(byte[] baseState, List<byte[]> updates) {
        Objects.requireNonNull(updates, "updates must not be null");
        List<String> encodedUpdates = updates.stream()
                .map(update -> Base64.getEncoder().encodeToString(requireNonEmpty(update, "update")))
                .toList();
        return new YjsMergeRequest(
                baseState == null ? null : Base64.getEncoder().encodeToString(requireNonEmpty(baseState, "baseState")),
                encodedUpdates);
    }

    /** 拒绝空二进制字段，避免合并服务收到无法应用的更新。 */
    private static byte[] requireNonEmpty(byte[] data, String fieldName) {
        Objects.requireNonNull(data, fieldName + " must not be null");
        if (data.length == 0) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return data;
    }
}
