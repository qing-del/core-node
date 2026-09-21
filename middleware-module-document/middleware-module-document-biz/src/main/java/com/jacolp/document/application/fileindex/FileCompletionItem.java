package com.jacolp.document.application.fileindex;

/** 补全接口允许返回的最小文件信息；权限字段只参与 ES 查询，不出现在响应中。 */
public record FileCompletionItem(String fileName, FileIndexResourceType resourceType,
                                 String resourceId, String resourceUrl) {
}
