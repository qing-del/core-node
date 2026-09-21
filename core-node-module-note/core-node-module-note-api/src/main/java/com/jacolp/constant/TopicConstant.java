package com.jacolp.constant;

public class TopicConstant {
    public static final int DEFAULT_SORT_ORDER = 0;
    public static final int MAX_TOPIC_NAME_LENGTH = 25;

    public static final String TOPIC_NOT_FOUND = "主题不存在";
    public static final String TOPIC_NAME_REQUIRED = "主题名称不能为空";
    public static final String TOPIC_NAME_TOO_LONG = "主题名称长度超出限制";
    public static final String TOPIC_ALREADY_EXISTS = "主题名称已存在";
    public static final String TOPIC_DELETE_NOT_ALLOWED_PREFIX = "该主题-";
    public static final String TOPIC_DELETE_NOT_ALLOWED_SUFFIX = "下有笔记，无法删除！";
    public static final String TOPIC_ID_INVALID = "主题ID非法";
    public static final String TOPIC_ADD_FAILED = "新增主题失败";
    public static final String TOPIC_UPDATE_FAILED = "修改主题失败";
    public static final String TOPIC_DELETE_FAILED = "删除主题失败";
    public static final String TOPIC_SUBMIT_AUDIT_FAILED = "提交主题审核失败";
    public static final String TOPIC_NOT_OWNER = "只能操作自己的主题";
}
