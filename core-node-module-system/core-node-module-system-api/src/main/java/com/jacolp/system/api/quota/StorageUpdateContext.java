package com.jacolp.system.api.quota;

import java.util.Map;

/**
 * Transitional ThreadLocal hand-off used by the existing storage aspect.
 * The map is keyed by user id and contains released storage bytes.
 */
public final class StorageUpdateContext {

    private static final ThreadLocal<Map<Long, Long>> USER_STORAGE_MAP = new ThreadLocal<>();

    private StorageUpdateContext() {
    }

    public static void setStorageMap(Map<Long, Long> map) {
        USER_STORAGE_MAP.set(map);
    }

    public static Map<Long, Long> getStorageMap() {
        return USER_STORAGE_MAP.get();
    }

    public static void clear() {
        USER_STORAGE_MAP.remove();
    }
}
