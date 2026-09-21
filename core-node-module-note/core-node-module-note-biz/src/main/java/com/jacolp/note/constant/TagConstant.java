package com.jacolp.note.constant;

public class TagConstant {
    public static final int MAX_TAG_NAME_LENGTH = 20;
    /**
     * 删除标签是否通过
     */
    public static final Short IS_PASS = 1;
    public static final Short IS_NOT_PASS = 0;

    public static final String TAG_NOT_FOUND = "标签不存在";
    public static final String TAG_ALREADY_EXISTS = "标签名称已存在";
    public static final String TAG_NAME_REQUIRED = "标签名称不能为空";
    public static final String TAG_NAME_TOO_LONG = "标签名称长度超出限制";
    public static final String TAG_ID_INVALID = "标签 ID 非法";
    public static final String TAG_ADD_FAILED = "标签新增失败";
    public static final String TAG_UPDATE_FAILED = "标签修改失败";
    public static final String TAG_DELETE_FAILED = "标签删除失败";
    public static final String TAG_DELETE_NOT_ALLOWED_PREFIX = "该标签-";
    public static final String TAG_DELETE_NOT_ALLOWED_SUFFIX = "正在被笔记使用，无法删除！";
    public static final String TAG_NOT_PASS = "标签未通过审核";
    public static final String TAG_REFERENCED = "标签正在被引用";
}
