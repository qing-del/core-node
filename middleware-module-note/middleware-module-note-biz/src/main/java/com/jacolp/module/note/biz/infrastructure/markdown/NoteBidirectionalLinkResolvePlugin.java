package com.jacolp.module.note.biz.infrastructure.markdown;

import com.jacolp.context.NoteImageResolveContext;
import com.jacolp.framework.markdown.converter.MarkdownPlugin;
import com.jacolp.module.media.api.MediaFileApi;
import com.jacolp.module.media.api.model.MediaFileSummary;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteEachMappingMapper;
import com.jacolp.module.note.biz.infrastructure.persistence.mapper.NoteImageMappingMapper;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteEachMappingDO;
import com.jacolp.module.note.biz.infrastructure.persistence.dataobject.NoteImageMappingDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 双链解析插件。
 * <p>
 * 负责在 Markdown 正文进入 Flexmark 解析之前，把 Obsidian 图片嵌入和双链笔记语法
 * 替换为标准输出：
 * <ul>
 *   <li>图片嵌入 {@code ![[img.png]]} → 标准 Markdown 图片 {@code ![alt](url)}</li>
 *   <li>笔记双链 {@code [[note.md#anchor|nickname]]} → 带有 data-* 属性的 {@code <a>} HTML 标签</li>
 * </ul>
 */
@Component
public class NoteBidirectionalLinkResolvePlugin implements MarkdownPlugin {

    /**
     * 统一匹配 ![[...]] 图片语法 和 [[...]] 双链语法，避免对同一字符串进行两次扫描。
     * group(1): "!" 表示图片嵌入，"" 表示普通双链。
     * group(2): [[ ]] 内的完整原始内容。
     */
    private static final Pattern UNIFIED_PATTERN = Pattern.compile("(!?)\\[\\[([^\\]]+)]]");

    @Autowired private MediaFileApi mediaFileApi;
    @Autowired private NoteImageMappingMapper noteImageMappingMapper;
    @Autowired private NoteEachMappingMapper noteEachMappingMapper;

    @Override
    public String preProcess(String rawMarkdown) {
        Long noteId = NoteImageResolveContext.getCurrentNoteId();
        if (noteId == null || !StringUtils.hasText(rawMarkdown)) {
            return rawMarkdown;
        }

        // ---- 批量加载图片映射 ----
        List<NoteImageMappingDO> imgMappings = noteImageMappingMapper.selectByNoteId(noteId);
        Map<String, NoteImageMappingDO> imageMappingMap = (imgMappings != null && !imgMappings.isEmpty())
                ? imgMappings.stream()
                        .filter(m -> m.getParsedImageName() != null)
                        .collect(Collectors.toMap(
                                m -> normalizeName(m.getParsedImageName()),
                                m -> m,
                                (left, right) -> left))
                : Map.of();

        // Batch read through the media boundary, avoiding N+1 and persistence leakage.
        ArrayList<Long> imageIds = imageMappingMap.values().stream()
                .map(NoteImageMappingDO::getImageId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Map<Long, MediaFileSummary> imageMap = imageIds.isEmpty()
                ? Map.of()
                : mediaFileApi.findByIds(imageIds);

        // ---- 批量加载笔记映射 ----
        // Key 设计：normalizeName(parsedNoteName) + "\0" + nullToEmpty(anchor)
        // 这样同一目标笔记的不同锚点链接可以精确命中各自的 mapping 行。
        List<NoteEachMappingDO> noteMappings = noteEachMappingMapper.selectBySourceNoteId(noteId);
        Map<String, NoteEachMappingDO> noteMappingMap =
                (noteMappings != null && !noteMappings.isEmpty())
                ? noteMappings.stream()
                        .filter(m -> m.getParsedNoteName() != null)
                        .collect(Collectors.toMap(
                                m -> buildNoteMappingKey(m.getParsedNoteName(), m.getAnchor()),
                                m -> m,
                                (left, right) -> left))
                : Map.of();

        return processLinks(rawMarkdown, imageMappingMap, imageMap, noteMappingMap);
    }

    /**
     * 单次扫描，同时处理图片嵌入语法（![[...]]）和双链笔记语法（[[note.md...]]）。
     * <p>
     * 分支逻辑：
     * <ul>
     *   <li>group(1) == "!" → 图片嵌入</li>
     *   <li>group(1) == "" 且 target 以 .md 结尾（含锚点前缀）→ 笔记双链</li>
     *   <li>其他 [[...]] 原样保留</li>
     * </ul>
     */
    private String processLinks(String rawMarkdown,
                                Map<String, NoteImageMappingDO> imageMappingMap,
                                Map<Long, MediaFileSummary> imageMap,
                                Map<String, NoteEachMappingDO> noteMappingMap) {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = UNIFIED_PATTERN.matcher(rawMarkdown);
        while (matcher.find()) {
            String prefix = matcher.group(1);  // "!" or ""
            String inner  = matcher.group(2);  // [[ ]] 内容

            // 拆出别名（| 之后的部分）
            String[] parts = inner.split("\\|", 2);
            String primary   = parts[0].trim();
            String secondary = parts.length > 1 && StringUtils.hasText(parts[1]) ? parts[1].trim() : null;

            String replacement;
            if (!prefix.isEmpty()) {
                // ![[image]] 图片嵌入
                String altText = secondary != null ? secondary : primary;
                NoteImageMappingDO mapping = imageMappingMap.get(normalizeName(primary));
                replacement = buildImageReplacement(mapping, altText, imageMap);
            } else {
                // 提取文件名部分（primary 可能含 #anchor，先拆出 # 之前的 filename）
                String filenamePart = primary;
                String anchorPart   = null;
                int hashIdx = primary.indexOf('#');
                if (hashIdx >= 0) {
                    filenamePart = primary.substring(0, hashIdx).trim();
                    String rawAnchor = primary.substring(hashIdx + 1).trim();
                    anchorPart = rawAnchor.isEmpty() ? null : rawAnchor;
                }

                String lowerFilename = filenamePart.toLowerCase(Locale.ROOT);

                // 拦截锚点，放置跳转空笔记问题
                if (filenamePart.isEmpty() && StringUtils.hasText(anchorPart)) {
                    String display = StringUtils.hasText(secondary) ? secondary : anchorPart;
                    // 渲染原生锚点：不加 internal-note-link，不加 data 属性，直接 href="#topic"
                    replacement = "<a href=\"#" + escapeAttr(anchorPart) + "\" class=\"hash-link\">" + escapeHtml(display) + "</a>";
                }
                // 如果不是常见图片/音视频后缀，就默认当做笔记双链处理
                else if (!lowerFilename.matches(".*\\.(png|jpg|jpeg|gif|webp|mp4|mp3|pdf)$")) {
                    replacement = buildNoteReplacement(filenamePart, anchorPart, secondary, noteMappingMap);
                } else {
                    // 其他 [[...]] 原样保留
                    replacement = matcher.group(0);
                }
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    /**
     * 把笔记双链转换为带有 {@code data-*} 属性的 {@code <a>} HTML 标签。
     * <p>
     * 成功命中（targetNoteId != null）：
     * <pre>{@code
     * <a href="/note?id=123#anchor" class="internal-note-link" data-note-id="123" data-anchor="anchor">display</a>
     * }</pre>
     * 未命中（targetNoteId == null 或 mapping 不存在）：
     * <pre>{@code
     * <a href="#" class="internal-note-link unresolved" data-note-id="null" data-anchor="anchor">display</a>
     * }</pre>
     *
     * @param filenamePart   笔记文件名部分（可能不含 .md，如 {@code "note"} 或 {@code "note.md"}）
     * @param anchorPart     锚点（如 {@code "cas原理"}），无则 null
     * @param nickname       别名（如 {@code "CAS原理"}），无则 null
     * @param noteMappingMap 预加载的 (noteName\0anchor) → NoteEachMappingDO 映射表
     */
    private String buildNoteReplacement(String filenamePart, String anchorPart, String nickname,
                                        Map<String, NoteEachMappingDO> noteMappingMap) {
        String noteName = stripMarkdownExtension(filenamePart);

        // 确定显示文本优先级：别名 > 锚点 > 笔记名
        String display = StringUtils.hasText(nickname) ? nickname
                : StringUtils.hasText(anchorPart) ? anchorPart
                : noteName;

        // 查 mapping
        String mapKey = buildNoteMappingKey(noteName, anchorPart);
        NoteEachMappingDO mapping = noteMappingMap.get(mapKey);
        Long targetNoteId = (mapping != null) ? mapping.getTargetNoteId() : null;

        if (targetNoteId != null) {
            // ---- 有效映射：组装完整 href ----
            StringBuilder href = new StringBuilder("/note?id=").append(targetNoteId);
            if (StringUtils.hasText(anchorPart)) {
                href.append('#').append(anchorPart);
            }
            return "<a href=\"" + href + "\" class=\"internal-note-link\""
                    + " data-note-id=\"" + targetNoteId + "\""
                    + (StringUtils.hasText(anchorPart) ? " data-anchor=\"" + escapeAttr(anchorPart) + "\"" : "")
                    + ">" + escapeHtml(display) + "</a>";
        } else {
            // ---- 未命中映射：占位链接，供前端识别 ----
            return "<a href=\"#\" class=\"internal-note-link unresolved\""
                    + " data-note-id=\"null\""
                    + (StringUtils.hasText(anchorPart) ? " data-anchor=\"" + escapeAttr(anchorPart) + "\"" : "")
                    + ">" + escapeHtml(display) + "</a>";
        }
    }

    /**
     * 根据 mapping 和 imageMap 构建图片 Markdown 替换串。
     * imageMap 已在调用前批量加载，此方法不触发任何网络 IO。
     */
    private String buildImageReplacement(NoteImageMappingDO mapping, String altText,
                                         Map<Long, MediaFileSummary> imageMap) {
        if (mapping == null || mapping.getImageId() == null) {
            return "![" + altText + "](#)";
        }
        MediaFileSummary image = imageMap.get(mapping.getImageId());
        if (image == null || !StringUtils.hasText(image.url())) {
            return "![" + altText + "](#)";
        }
        return "![" + altText + "](" + image.url() + ")";
    }

    // ==================== 工具方法 ====================

    /**
     * 构建笔记映射表的复合查找 Key：{@code normalizeName(noteName) + "\0" + nullToEmpty(anchor)}。
     * 使用 NUL 字符（\0）作为分隔符，避免笔记名与锚点值之间出现歧义。
     */
    private String buildNoteMappingKey(String noteName, String anchor) {
        return normalizeName(noteName) + "\0" + (anchor == null ? "" : anchor.trim());
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String stripMarkdownExtension(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md")) {
            return name.substring(0, name.length() - 3);
        }
        int slashIndex = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slashIndex >= 0 ? name.substring(slashIndex + 1) : name;
    }

    /** HTML 属性值转义（用于 data-anchor 等属性值）。 */
    private String escapeAttr(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("\"", "&quot;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }

    /** HTML 文本内容转义（用于链接显示文本）。 */
    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
    }
}
