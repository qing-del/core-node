package com.jacolp.document.enums;

/**
 * 文档级直接授权权限。
 *
 * <p>{@link #WRITE} 隐含 {@link #READ}；所有者不是此枚举中的一种授权记录，
 * 而是由文档自身的所有者字段确定。</p>
 */
public enum DocumentPermission {

    /** 只能读取文档和接收实时更新。 */
    READ,

    /** 可以读取并提交文档正文更新。 */
    WRITE;

    /**
     * 判断该权限是否允许读取。
     *
     * @return 对 READ、WRITE 均为 {@code true}
     */
    public boolean canRead() {
        return true;
    }

    /**
     * 判断该权限是否允许写入。
     *
     * @return 仅 WRITE 为 {@code true}
     */
    public boolean canWrite() {
        return this == WRITE;
    }
}
