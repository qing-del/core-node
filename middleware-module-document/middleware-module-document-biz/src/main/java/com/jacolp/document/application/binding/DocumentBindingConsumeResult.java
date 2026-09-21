package com.jacolp.document.application.binding;

/** 一次 Binding Stream drain 的可观测结果。 */
public record DocumentBindingConsumeResult(long documentId, int processedCount, long deletedCount) {

    /** 返回没有待处理 Binding 的结果。 */
    public static DocumentBindingConsumeResult empty(long documentId) {
        return new DocumentBindingConsumeResult(documentId, 0, 0L);
    }
}
