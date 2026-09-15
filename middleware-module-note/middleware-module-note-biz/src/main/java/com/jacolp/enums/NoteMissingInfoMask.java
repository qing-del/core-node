package com.jacolp.enums;

import lombok.Getter;

/**
 * 笔记缺失信息掩码枚举
 * <p>
 * 使用位掩码表示缺失的信息类型：
 * <ul>
 *   <li>0b001 (1) - 缺失标签</li>
 *   <li>0b010 (2) - 缺失图片</li>
 *   <li>0b100 (4) - 缺失内联笔记</li>
 *   <li>0b000 (0) - 信息完整</li>
 * </ul>
 */
@Getter
public enum NoteMissingInfoMask {
    /**
     * 完成状态
     */
    COMPLETE(0b000, "信息完整"),

    /**
     * 缺失标签
     */
    TAG(0b001, "缺失标签"),
    /**
     * 缺失图片
     */
    IMAGE(0b010, "缺失图片"),
    /**
     * 缺失内联笔记
     */
    NOTE(0b100, "缺失内联笔记");

    private final int mask;
    private final String desc;

    NoteMissingInfoMask(int mask, String desc) {
        this.mask = mask;
        this.desc = desc;
    }

    /**
     * 检查是否缺失标签
     *
     * @param value 掩码值
     * @return true=缺失标签, false=不缺失
     */
    public static boolean isTagMissing(int value) {
        return (value & TAG.mask) != 0;
    }

    /**
     * 检查是否缺失图片
     *
     * @param value 掩码值
     * @return true=缺失图片, false=不缺失
     */
    public static boolean isImageMissing(int value) {
        return (value & IMAGE.mask) != 0;
    }

    /**
     * 检查是否缺失内联笔记
     *
     * @param value 掩码值
     * @return true=缺失内联笔记, false=不缺失
     */
    public static boolean isNoteMissing(int value) {
        return (value & NOTE.mask) != 0;
    }

    /**
     * 检查信息是否完整
     *
     * @param value 掩码值
     * @return true=完整, false=有缺失
     */
    public static boolean isComplete(int value) {
        return value == 0;
    }
}
