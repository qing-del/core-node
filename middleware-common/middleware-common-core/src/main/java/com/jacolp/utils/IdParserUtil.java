package com.jacolp.utils;

import com.jacolp.exception.BaseException;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * ID 解析工具类。
 * <p>
 * 提供从字符串中解析 ID 列表的通用方法，支持逗号分隔的 ID 字符串。
 */
public class IdParserUtil {

    /**
     * 从逗号分隔的字符串中解析 ID 列表。
     * <p>
     * 说明：
     * - 自动去除空白字符；
     * - 自动去重并保持输入顺序；
     * - 跳过空字符串片段。
     *
     * @param ids 逗号分隔的 ID 字符串，例如 "1,2,3"
     * @param idTypeName ID 类型名称，用于错误提示，例如 "标签"、"主题"
     * @return 解析后的 ID 列表
     * @throws BaseException 当 ID 字符串为空或包含非法格式时抛出异常
     */
    public static List<Long> parseIds(String ids, String idTypeName) {
        if (!StringUtils.hasText(ids)) {
            throw new BaseException("待删除的" + idTypeName + " ID 列表不能为空");
        }

        /*
         * LinkedHashSet 作用：
         * 1. 去重，避免重复 ID 干扰删除校验；
         * 2. 保持输入顺序，便于日志排查。
         */
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        String[] parts = ids.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            try {
                idSet.add(Long.valueOf(trimmed));
            } catch (NumberFormatException ex) {
                throw new BaseException(idTypeName + " ID 非法: " + trimmed);
            }
        }

        List<Long> result = new ArrayList<>(idSet);
        if (result.isEmpty()) {
            throw new BaseException("待删除的" + idTypeName + " ID 列表不能为空");
        }
        return result;
    }
}
