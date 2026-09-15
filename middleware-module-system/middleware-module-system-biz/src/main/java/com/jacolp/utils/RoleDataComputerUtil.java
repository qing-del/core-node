package com.jacolp.utils;

import com.jacolp.constant.RoleConstant;

import java.util.HashMap;
import java.util.Map;

public class RoleDataComputerUtil {
    private final static Map<Long, Long> STORAGE_MAP = new HashMap<>();
    private final static Map<Long, Integer> API_LIMIT_MAP = new HashMap<>();

    /**
     * 添加角色最大存储空间
     * @param roleId
     * @param storage
     */
    public static void putStorage(long roleId, long storage) {
        STORAGE_MAP.put(roleId, storage);
    }

    /**
     * 获取角色的最大存储空间
     * @param roleId
     * @return
     */
    public static long getStorage(long roleId) {
        return STORAGE_MAP.getOrDefault(roleId, RoleConstant.USER_MAX_STORAGE_BYTES);
    }

    /**
     * 添加角色 API 调用次数限制
     * @param roleId
     * @param limit
     */
    public static void putApiLimit(long roleId, int limit) {
        API_LIMIT_MAP.put(roleId, limit);
    }

    /**
     * 获取角色的 API 调用次数限制
     * @param roleId
     * @return
     */
    public static int getApiLimit(long roleId) {
        return API_LIMIT_MAP.getOrDefault(roleId, 0);
    }
}
