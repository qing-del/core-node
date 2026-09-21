package com.jacolp.constant;

public class NoteConstant {

    /**
     * 笔记文件大小上限：300KB = 300 * 1024 字节
     * MD文件内容存储在 biz_note_context 表中
     */
    public static final int MAX_NOTE_FILE_SIZE_BYTES = 300 * 1024;

    /**
     * 笔记文件允许的格式：.md
     */
    public static final String ALLOWED_NOTE_FORMAT = ".md";

    /**
     * 笔记存储类型：0-本地存储，1-阿里云OSS，2-Cloudflare R2
     */
    public static final Integer DEFAULT_STORAGE_TYPE = 0;
    public static final Integer STORAGE_TYPE_ALIYUN_OSS = 1;
    public static final Integer STORAGE_TYPE_CLOUDFLARE_R2 = 2;

    /**
     * 笔记 diff 状态：0-待确认，1-已确认，2-已取消
     */
    public static final Integer NOTE_DIFF_STATUS_PENDING = 0;
    public static final Integer NOTE_DIFF_STATUS_CONFIRMED = 1;
    public static final Integer NOTE_DIFF_STATUS_CANCELED = 2;

    public static final String NOTE_DIFF_EXIST = "已存在未处理的改变";

    /**
     * 笔记是否正在修改：0-否，1-是
     */
    public static final Short NOT_CHANGING = 0;
    public static final Short IS_CHANGING = 1;

    /**
     * 笔记是否删除：0-未删除，1-已删除
     */
    public static final Short NOT_DELETED = 0;
    public static final Short DELETED = 1;

    /**
     * 笔记是否缺失关联信息：0-未缺失，1-缺失
     */
    public static final Short NOT_MISSED_INFO = 0;
    public static final Short MISSED_INFO = 1;

    /**
     * 笔记是否跨用户：0-否，1-是
     */
    public static final Short NOT_IS_CROSS_USER = 0;
    public static final Short IS_CROSS_USER = 1;

    /**
     * 笔记状态常量
     */
    public static final Short STATUS_NEW = 0;
    public static final Short STATUS_PENDING_INFO = 1;
    public static final Short STATUS_READY_TO_CONVERT = 2;
    public static final Short STATUS_CONVERTED = 3;
    public static final Short STATUS_PENDING_AUDIT = 4;
    public static final Short STATUS_APPROVED = 5;
    public static final Short STATUS_PUBLISHED = 6;
    public static final Short STATUS_REJECTED = 7;
    public static final Short STATUS_DELETED = 8;

    /**
     * 缺失信息掩码常量
     */
    public static final Integer MISSING_TAG = 0b001;
    public static final Integer MISSING_IMAGE = 0b010;
    public static final Integer MISSING_NOTE = 0b100;

    public static final String NOTE_NOT_FOUND = "未找到目标笔记";
    public static final String NOTE_CONTENT_NOT_FOUND = "笔记内容未找到";
    public static final String NOTE_CHANGE_DIFF_NOT_FOUND = "未找到待确认的笔记变更记录";
    public static final String NOTE_FILE_EMPTY = "上传的笔记文件为空";
    public static final String NOTE_FILE_TOO_LARGE = "笔记文件大小超出系统限制";
    public static final String NOTE_INVALID_FORMAT = "不支持的笔记格式（仅支持 .md）";
    public static final String NOTE_MISSING_INFO = "笔记存在缺失的标签、图片、内联笔记绑定，请补充后再转换";
    public static final String NOTE_NOT_CONVERTED = "笔记尚未转换，请先执行转换操作";
    public static final String NOTE_NOT_PUBLISHED = "笔记尚未发布";
    public static final String NOTE_STORAGE_QUOTA_EXCEEDED = "存储空间不足，无法上传笔记";
    public static final String NOTE_FILE_READ_ERROR = "笔记文件读取失败";
    public static final String NOTE_FILE_WRITE_ERROR = "笔记文件写入失败";
    public static final String NOTE_CONVERT_FAILED = "笔记转换失败";
    public static final String NOTE_UPDATE_FAILED = "笔记更新失败";
    public static final String NOTE_DELETE_FAILED = "笔记删除失败";
    public static final String NOTE_VISIBLE_UPDATE_FAILED = "笔记公开状态更新失败";
    public static final String NOTE_EACH_NOT_PASS = "内联笔记未通过审核";
    public static final String NOTE_ID_INVALID = "笔记 ID 不合法";
    public static final String NOTE_NOT_OWNER = "只能操作自己的笔记";
    public static final String NOTE_ALREADY_PASSED = "该笔记已通过审核";
    public static final String NOTE_AUDIT_PENDING = "该笔记已有待审核的申请";
    public static final String NOTE_NOT_PASS = "该笔记未通过审核";
    public static final String DELETE_NOTE_NOT_EXIST = "需要删除的笔记不存在";
    public static final String NOTE_UPLOAD_FAILED = "笔记上传失败";
    public static final String NOTE_STATUS_NOT_ALLOWED = "笔记状态不允许执行此操作";


    private NoteConstant() {
    }
}
