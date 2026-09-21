package com.jacolp.document.application.yjs;

/** 合并服务无法返回有效的合并后 Yjs 状态时抛出。 */
public class YjsMergeException extends RuntimeException {

    /** 创建不带底层原因的合并服务异常。 */
    public YjsMergeException(String message) {
        super(message);
    }

    /** 保留 HTTP、序列化或 Base64 解码错误作为根因。 */
    public YjsMergeException(String message, Throwable cause) {
        super(message, cause);
    }
}
