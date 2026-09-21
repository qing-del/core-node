package com.jacolp.document.websocket;

import com.jacolp.document.websocket.exception.DocumentRoomLimitExceededException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 为单个文档 Room 中的 WebSocket Session 分配临时光标颜色。
 *
 * <p>颜色属于连接会话运行态，不写入数据库、Redis 或文档正文。分配器自身不跨 Room
 * 共享，因此只需要保证当前 Room 内的颜色唯一。</p>
 */
final class DocumentCursorColorAllocator {

    private static final int DEFAULT_PALETTE_SIZE = 64;
    private static final List<String> DEFAULT_PALETTE = createDefaultPalette();

    private final List<String> palette;
    private final Map<String, String> colorsBySession = new HashMap<>();
    private final Set<String> occupiedColors = new HashSet<>();

    /** 创建使用固定高对比色板的 Room 颜色分配器。 */
    DocumentCursorColorAllocator() {
        this(DEFAULT_PALETTE);
    }

    /** 创建指定色板的分配器；测试可用小色板验证碰撞和耗尽行为。 */
    DocumentCursorColorAllocator(List<String> palette) {
        Objects.requireNonNull(palette, "palette must not be null");
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("palette must not be empty");
        }
        List<String> normalized = palette.stream()
                .map(DocumentCursorColorAllocator::requireColor)
                .distinct()
                .toList();
        if (normalized.size() != palette.size()) {
            throw new IllegalArgumentException("palette must contain distinct colors");
        }
        this.palette = List.copyOf(normalized);
    }

    /**
     * 为 Session 分配颜色；同一个 Session 重复调用时返回原颜色。
     *
     * <p>以 Session ID 的稳定哈希选择起点，再在线性探测中跳过当前 Room 已占用颜色，
     * 这样既能保持分配结果可预测，也能避免简单哈希碰撞。</p>
     */
    synchronized String allocate(String sessionId) {
        requireSessionId(sessionId);
        String existing = colorsBySession.get(sessionId);
        if (existing != null) {
            return existing;
        }

        int start = Math.floorMod(sessionId.hashCode(), palette.size());
        for (int offset = 0; offset < palette.size(); offset++) {
            String candidate = palette.get((start + offset) % palette.size());
            if (occupiedColors.add(candidate)) {
                colorsBySession.put(sessionId, candidate);
                return candidate;
            }
        }
        throw new DocumentRoomLimitExceededException("document room cursor color capacity exceeded");
    }

    /** 释放 Session 的颜色；重复释放和未知 Session 都保持幂等。 */
    synchronized void release(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String color = colorsBySession.remove(sessionId);
        if (color != null) {
            occupiedColors.remove(color);
        }
    }

    /** 返回当前已经分配的颜色数量，供 Room 测试和运行态保护使用。 */
    synchronized int size() {
        return colorsBySession.size();
    }

    /** 校验并规范颜色格式，避免非法值进入后续 CSS 渲染。 */
    private static String requireColor(String color) {
        if (color == null || !color.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("cursor color must be a six-digit hexadecimal CSS color");
        }
        return color.toUpperCase(Locale.ROOT);
    }

    /** 校验 Session ID 是可用于 Room 运行态索引的非空字符串。 */
    private static void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }

    /**
     * 生成 64 个固定的 HSL 组合色，覆盖多组色相和明度，足以满足当前 Room 上限。
     * 生成规则是常量，服务重启不会影响单个 Room 内的唯一性和颜色格式。
     */
    private static List<String> createDefaultPalette() {
        List<String> colors = new ArrayList<>(DEFAULT_PALETTE_SIZE);
        double[] lightness = {0.40d, 0.50d, 0.60d, 0.70d};
        double[] saturation = {0.82d, 0.82d, 0.78d, 0.70d};
        for (int level = 0; level < lightness.length; level++) {
            for (int hueIndex = 0; hueIndex < 16; hueIndex++) {
                colors.add(hslToHex(hueIndex * 22.5d, saturation[level], lightness[level]));
            }
        }
        return List.copyOf(colors);
    }

    /** 将 HSL 颜色转换为标准 #RRGGBB 字符串，避免引入额外颜色依赖。 */
    private static String hslToHex(double hue, double saturation, double lightness) {
        double chroma = (1d - Math.abs(2d * lightness - 1d)) * saturation;
        double normalizedHue = hue / 60d;
        double second = chroma * (1d - Math.abs(normalizedHue % 2d - 1d));
        double red;
        double green;
        double blue;
        if (normalizedHue < 1d) {
            red = chroma;
            green = second;
            blue = 0d;
        } else if (normalizedHue < 2d) {
            red = second;
            green = chroma;
            blue = 0d;
        } else if (normalizedHue < 3d) {
            red = 0d;
            green = chroma;
            blue = second;
        } else if (normalizedHue < 4d) {
            red = 0d;
            green = second;
            blue = chroma;
        } else if (normalizedHue < 5d) {
            red = second;
            green = 0d;
            blue = chroma;
        } else {
            red = chroma;
            green = 0d;
            blue = second;
        }
        double match = lightness - chroma / 2d;
        int redByte = toByte(red + match);
        int greenByte = toByte(green + match);
        int blueByte = toByte(blue + match);
        return "#%02X%02X%02X".formatted(redByte, greenByte, blueByte);
    }

    /** 把 0 到 1 的通道值转换为 0 到 255 的整数。 */
    private static int toByte(double channel) {
        return (int) Math.round(Math.max(0d, Math.min(1d, channel)) * 255d);
    }
}
