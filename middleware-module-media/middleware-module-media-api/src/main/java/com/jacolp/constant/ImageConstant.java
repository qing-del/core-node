package com.jacolp.constant;

/**
 * 图片接口错误消息与常量。
 */
public class ImageConstant {

    // 图片大小与格式限制
    public static final int MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    public static final long DEFAULT_MAX_IMAGE_BYTES = 2 * 1024 * 1024; // 2MB 默认上传限制
    
    // 允许的图片格式后缀（小写）
    public static final String[] ALLOWED_IMAGE_FORMATS = {
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".bmp"
    };

    // ================ 存储类型常量 ================
    public static final short STORAGE_TYPE_ALIYUN_OSS = 1;      // 阿里云 OSS
    public static final short STORAGE_TYPE_CLOUDFLARE_R2 = 2;   // Cloudflare R2（预留）

    // 默认先使用阿里云 OSS
    public static final short DEFAULT_STORAGE_TYPE = STORAGE_TYPE_ALIYUN_OSS;

    // ================ 存储路径常量 ================
    public static final String IMAGE_OSS_DIRECTORY_PREFIX = "image";  // OSS Object Key 前缀

    // ================ 公开状态常量 ================
    public static final short IS_PUBLIC_NO = 0;   // 私有
    public static final short IS_PUBLIC_YES = 1;  // 公开

    // ================ 审核状态常量 ================
    public static final short AUDIT_STATUS_PENDING = 0;   // 待审核
    public static final short AUDIT_STATUS_AUDITING = 1;  // 审核中
    public static final short AUDIT_STATUS_APPROVED = 2;  // 已通过
    public static final short AUDIT_STATUS_REJECTED = 3;  // 已拒绝
    public static final short AUDIT_STATUS_DELETED = 4;   // 已删除

    // ============= 图片删除死信队列常量 =============
    public static final short IMAGE_DELETE_DEAD_LETTER_STATUS_WAITING = 0;    // 等待处理
    public static final short IMAGE_DELETE_DEAD_LETTER_STATUS_COMPLETED = 1;  // 处理完成

    public static final String FAILED_TO_INSERT_IMAGE_DELETE_DEAD_LETTER = "图片删除死信队列插入失败";

    // ================ 错误消息 ================
    public static final String IMAGE_EMPTY_FILENAME = "文件名不能为空";
    public static final String IMAGE_NOT_FOUND = "未找到目标图片";
    public static final String IMAGE_FILE_EMPTY = "上传的图片文件为空";
    public static final String IMAGE_FILE_TOO_LARGE = "图片文件大小超出系统限制";
    public static final String IMAGE_INVALID_FORMAT = "不支持的图片格式";
    public static final String IMAGE_NAME_DUPLICATE = "该主题下已存在同名图片";
    public static final String IMAGE_TRANSFER_FAILED = "图片存储介质转换失败";
    public static final String IMAGE_STORAGE_TYPE_MISMATCH = "图片当前存储类型与操作不符";
    public static final String IMAGE_IN_USE = "图片正在被笔记引用，无法删除";
    public static final String IMAGE_STORAGE_QUOTA_EXCEEDED = "存储空间不足，无法上传图片";
    public static final String IMAGE_AUDIT_NOT_FOUND = "未找到目标审核记录";
    public static final String IMAGE_AUDIT_ALREADY_PROCESSED = "该审核记录已处理";
    public static final String IMAGE_NOT_OWNER = "只能操作自己的图片";
    public static final String IMAGE_STORAGE_PROVIDER_NOT_SUPPORTED = "当前存储类型暂不支持，请后续接入对应云存储实现";
    public static final String IMAGE_REJECT_REASON_NOT_EMPTY = "拒绝原因不能为空";
    public static final String IMAGE_NOT_PASS = "图片未通过审核";
}
