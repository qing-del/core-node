package com.jacolp.utils;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 集合操作工具类。
 * <p>提供字符串列表的去重、空白过滤等通用操作。
 * 使用 {@link LinkedHashSet} 保证去重后的顺序与输入一致。</p>
 *
 * @see LinkedHashSet
 */
public class CollectionUtil {

    /**
     * 对字符串列表去重并过滤空白项，保留原始顺序。
     * <p>处理流程：</p>
     * <ol>
     *   <li>null / 空列表 → 返回空列表</li>
     *   <li>每个元素 trim() 去首尾空白</li>
     *   <li>{@link StringUtils#hasText(String)} 过滤空白字符串</li>
     *   <li>{@link LinkedHashSet} 去重，保留首次出现的顺序</li>
     * </ol>
     *
     * <p>典型使用场景：笔记扫描出的标签列表、图片名列表去重。</p>
     *
     * @param values 可能含重复项和空白字符串的列表
     * @return 去重后的有序列表；输入为 null 时返回空列表
     */
    public static List<String> normalizeDistinctList(List<String> values) {
        // 快速返回：null 或空列表无需处理
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        // LinkedHashSet: 去重 + 保持插入顺序
        Set<String> set = new LinkedHashSet<>();
        for (String value : values) {
            // 仅保留非空字符串；trim() 去除首尾空格
            if (StringUtils.hasText(value)) {
                set.add(value.trim());
            }
        }
        // 转回 ArrayList 供调用方使用（便于序列化、随机访问）
        return new ArrayList<>(set);
    }
}
