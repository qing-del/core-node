package com.jacolp.framework.markdown.converter;

import com.jacolp.framework.markdown.io.FileStorageService;
import com.jacolp.framework.markdown.converter.MarkdownHtmlEngine.FrontMatter;
import com.jacolp.framework.markdown.converter.MarkdownHtmlEngine.HtmlProcessResult;

import java.nio.file.Path;
import java.util.List;

/**
 * Markdown 发布服务门面（Facade）。
 * <p>
 * 职责：将 {@link MarkdownHtmlEngine}（纯解析引擎）和 {@link FileStorageService}（存储抽象层）
 * 串联起来，完成从"原始 Markdown 文本"到"持久化 HTML 页面"的完整发布流程。
 * </p>
 *
 * <h3>设计理念</h3>
 * <p>
 * 本类本身不做任何"解析"或"存储"的具体工作，它只负责编排（Orchestration）：
 * <ol>
 *     <li>调用引擎解析 Markdown → 拿到结构化结果</li>
 *     <li>用结果组装完整的 HTML 页面（CSS + JS + 正文 + TOC）</li>
 *     <li>把成品 HTML 交给存储服务保存</li>
 * </ol>
 * 这样每一层的职责都非常清晰，改 CSS 不会碰到解析逻辑，换存储方式也不会碰到 HTML 模板。
 * </p>
 *
 * <h3>Spring Boot 集成提示</h3>
 * <p>
 * 迁移到 Spring Boot 后，只需在本类上加 {@code @Service} 注解。
 * 构造函数的两个参数 {@code engine} 和 {@code storageService} 会被 Spring
 * 通过构造器注入（Constructor Injection）自动装配——这是 Spring 官方推荐的注入方式。
 * </p>
 */
public class MarkdownPublishService {

    /** Markdown 纯解析引擎（无状态，线程安全，可复用）。 */
    private final MarkdownHtmlEngine engine;

    /** 文件存储服务（通过接口注入，支持本地存储、云存储等多种实现）。 */
    private final FileStorageService storageService;

    /**
     * 构造 MarkdownPublishService 实例。
     * <p>
     * 通过构造函数注入依赖，而不是在内部 new 对象。这样做的好处：
     * <ul>
     *     <li>方便测试：单元测试时可以传入 Mock 对象</li>
     *     <li>方便扩展：换一种存储实现只需传入不同的 {@link FileStorageService}</li>
     *     <li>方便集成：Spring Boot 可直接通过构造器注入自动装配</li>
     * </ul>
     *
     * @param engine         Markdown 解析引擎（负责 Markdown → 结构化 HTML）
     * @param storageService 文件存储服务（负责将最终 HTML 持久化保存）
     */
    public MarkdownPublishService(MarkdownHtmlEngine engine, FileStorageService storageService) {
        this.engine = engine;
        this.storageService = storageService;
    }

    // ==================== 公开发布入口 ====================

    /**
     * 将单个 Markdown 文件解析、套壳并发布为 HTML 页面。
     * <p>
     * 完整的数据流编排：
     * <ol>
     *     <li>从文件路径提取不含扩展名的文件名，作为备选标题（fallbackTitle）</li>
     *     <li>调用 {@link MarkdownHtmlEngine#process(String)} 解析 Markdown，获取结构化结果</li>
     *     <li>若引擎返回的 {@link FrontMatter} 中无标题，使用 fallbackTitle 兜底</li>
     *     <li>调用 {@link #wrapHtml} 将正文、元数据、TOC 组装为完整 HTML 页面</li>
     *     <li>计算输出文件的相对路径（.md → .html）</li>
     *     <li>通过 {@link FileStorageService#save} 持久化保存最终 HTML</li>
     * </ol>
     *
     * @param markdownFilePath 源 Markdown 文件路径（用于提取文件名和计算相对路径）
     * @param rawMarkdown      原始 Markdown 文本内容
     * @param notesDir         笔记根目录（用于计算输出时的相对路径）
     */
    public void publish(Path markdownFilePath, String rawMarkdown, Path notesDir) {
        // ① 提取文件名（不含扩展名）作为备选标题
        String fallbackTitle = toTitle(markdownFilePath);

        // ② 调用引擎完成纯文本解析（Front-Matter、WikiLink、Flexmark、后处理、TOC）
        HtmlProcessResult result = engine.process(rawMarkdown);

        // ③ 若 Front-Matter 中没有声明 title，使用文件名作为兜底
        FrontMatter meta = result.meta().withFallbackTitle(fallbackTitle);

        // ④ 组装完整的 HTML 页面（外壳模板 + CSS + JS + TOC）
        String finalHtml = wrapHtml(result.bodyHtml(), meta, fallbackTitle, result.tocHtml());

        // ⑤ 计算输出相对路径：将 "笔记/子目录/文章.md" 转换为 "子目录/文章.html"
        String htmlRelativePath = toHtmlRelativePath(markdownFilePath, notesDir);

        // ⑥ 交给存储服务保存——具体写到哪里，由注入的实现类决定
        storageService.save(htmlRelativePath, finalHtml);
    }

    // ==================== HTML 外壳模板 ====================

    /*
     * ╔══════════════════════════════════════════════════════════════════╗
     * ║  📐 CSS / JS 修改指南（写给未来的你）                           ║
     * ║                                                                ║
     * ║  • 改页面背景色 → 找 body 的 background 属性                    ║
     * ║  • 改正文区域宽度 → 找 .container 的 max-width                  ║
     * ║  • 改正文字体 → 找 body 的 font-family                          ║
     * ║  • 改代码块主题色 → 找 pre 的 background 和 color               ║
     * ║  • 改标签徽章颜色 → 找 .tag 的 background 和 color              ║
     * ║  • 改 Callout 样式 → 找 .callout-xxx 系列                      ║
     * ║  • 改 TOC 侧边栏宽度 → 找 .toc-sidebar 的 width                ║
     * ║  • 改 Mermaid CDN 版本 → 找 mermaidScript 中的 @11              ║
     * ║                                                                ║
     * ║  所有样式都集中在 wrapHtml 的 <style> 块中，一目了然。           ║
     * ╚══════════════════════════════════════════════════════════════════╝
     */

    /**
     * 将引擎输出的 bodyHtml、元数据和 TOC 组装为一个完整的 HTML 页面。
     * <p>
     * 包含：页面标题、全量内联 CSS 样式、元数据渲染（标题/创建时间/标签）、
     * TOC 侧边栏、Mermaid.js CDN 脚本（按需引入）、TOC 交互脚本（按需引入）。
     * <p>
     * 关于 {@code %%}：在 Java 的 {@code String.formatted()} 中，
     * {@code %} 是格式化占位符前缀，若要输出字面量 {@code %}（如 CSS 的 {@code 100%%}），
     * 需要写成 {@code %%} 进行转义。
     *
     * @param htmlBody      经引擎处理后的 HTML 正文
     * @param meta          Front-Matter 元数据（已经过 fallbackTitle 兜底）
     * @param fallbackTitle 备选页面标题（当 meta.title() 仍为空时使用）
     * @param tocHtml       TOC 侧边栏 HTML（可为空字符串）
     * @return 完整的 HTML 文档字符串
     */
    private static String wrapHtml(String htmlBody, FrontMatter meta, String fallbackTitle, String tocHtml) {
        // 确定页面 <title> 标签内容：优先使用 Front-Matter 标题，否则使用文件名
        String title = MarkdownHtmlEngine.escapeHtml(
                meta.title() == null || meta.title().isBlank() ? fallbackTitle : meta.title());

        // 将元数据渲染为 HTML 片段（标题、创建时间、标签徽章）
        String metadata = renderMetadata(meta);

        // 判断是否存在 TOC 内容
        boolean hasToc = tocHtml != null && !tocHtml.isBlank();

        // 按需引入 Mermaid.js CDN 脚本：仅当正文包含 Mermaid 图表时才加载
        String mermaidScript = htmlBody.contains("class=\"mermaid\"")
                ? """
                  <script type=\"module\">
                      import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';
                      mermaid.initialize({ startOnLoad: true, theme: 'default' });
                  </script>
                  """
                : "";

        // 按需引入 TOC 交互脚本：包含侧边栏收起/展开和 FAB 浮动按钮拖拽功能
        String tocScript = hasToc
                ? """
                  <script>
                      (function () {
                          const sidebar = document.getElementById('tocSidebar');
                          const collapseBtn = document.getElementById('tocCollapseBtn');
                          const fab = document.getElementById('tocFab');
                          if (!sidebar || !collapseBtn || !fab) {
                              return;
                          }

                          const collapse = () => {
                              sidebar.classList.add('is-collapsed');
                              fab.classList.add('is-visible');
                          };

                          const expand = () => {
                              sidebar.classList.remove('is-collapsed');
                              fab.classList.remove('is-visible');
                          };

                          collapseBtn.addEventListener('click', collapse);
                          fab.addEventListener('click', expand);

                          let dragging = false;
                          let moved = false;
                          let startX = 0;
                          let startY = 0;
                          let offsetX = 0;
                          let offsetY = 0;

                          fab.style.left = '24px';
                          fab.style.top = '24px';

                          fab.addEventListener('pointerdown', function (event) {
                              dragging = true;
                              moved = false;
                              startX = event.clientX;
                              startY = event.clientY;
                              const rect = fab.getBoundingClientRect();
                              offsetX = startX - rect.left;
                              offsetY = startY - rect.top;
                              fab.setPointerCapture(event.pointerId);
                          });

                          fab.addEventListener('pointermove', function (event) {
                              if (!dragging) {
                                  return;
                              }
                              const dx = Math.abs(event.clientX - startX);
                              const dy = Math.abs(event.clientY - startY);
                              if (dx > 3 || dy > 3) {
                                  moved = true;
                              }
                              const left = event.clientX - offsetX;
                              const top = event.clientY - offsetY;
                              const maxLeft = window.innerWidth - fab.offsetWidth - 8;
                              const maxTop = window.innerHeight - fab.offsetHeight - 8;
                              fab.style.left = Math.max(8, Math.min(maxLeft, left)) + 'px';
                              fab.style.top = Math.max(8, Math.min(maxTop, top)) + 'px';
                          });

                          fab.addEventListener('pointerup', function (event) {
                              if (dragging) {
                                  fab.releasePointerCapture(event.pointerId);
                              }
                              dragging = false;
                              if (!moved) {
                                  expand();
                              }
                          });

                          fab.addEventListener('click', function (event) {
                              if (moved) {
                                  event.preventDefault();
                                  event.stopPropagation();
                              }
                          }, true);
                      })();
                  </script>
                  """
                : "";

        // ─────────────────────────────────────────────────────────────
        // 组装最终的 HTML 页面模板
        // 占位符顺序：%s = title, tocHtml, metadata, htmlBody, mermaidScript, tocScript
        // ─────────────────────────────────────────────────────────────
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                    <style>
                        /* ═══════ 全局基础样式 ═══════ */
                        /* 💡 改页面背景色：修改 body 的 background */
                        /* 💡 改正文字体：修改 body 的 font-family */
                        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; background: #f7f8fa; color: #222; }
                        /* 💡 改正文区域宽度：修改 max-width */
                        .container { max-width: 960px; margin: 24px auto; background: #fff; padding: 28px 32px; border-radius: 12px; box-shadow: 0 6px 24px rgba(0,0,0,.06); }
                        h1,h2,h3,h4,h5,h6 { margin-top: 1.4em; }
                        p,li { line-height: 1.75; }

                        /* ═══════ 代码块样式 ═══════ */
                        /* 💡 改代码块主题色：修改 pre 的 background 和 color */
                        code { background: #f3f4f6; padding: 2px 6px; border-radius: 6px; }
                        pre { background: #0f172a; color: #e2e8f0; padding: 14px; border-radius: 10px; overflow-x: auto; }
                        pre code { background: transparent; color: inherit; padding: 0; }

                        /* ═══════ 引用块样式 ═══════ */
                        blockquote { border-left: 4px solid #d0d7de; margin: 16px 0; padding: 6px 14px; color: #57606a; background: #f6f8fa; }

                        /* ═══════ 表格样式 ═══════ */
                        table { border-collapse: collapse; width: 100%%; margin: 16px 0; }
                        th, td { border: 1px solid #d0d7de; padding: 8px 10px; text-align: left; }
                        thead { background: #f6f8fa; }

                        /* ═══════ 元数据区域样式 ═══════ */
                        .meta-title { margin: 0 0 8px 0; }
                        .meta-row { margin-bottom: 14px; color: #555; font-size: 14px; }
                        /* 💡 改标签徽章颜色：修改 .tag 的 background 和 color */
                        .tag { display: inline-block; background: #eef2ff; color: #3949ab; border-radius: 999px; padding: 2px 10px; margin-right: 8px; }

                        /* ═══════ Callout 样式（Obsidian 风格提示框） ═══════ */
                        /* 💡 新增 Callout 类型：复制一行 .callout-xxx 并修改颜色即可 */
                        .callout { border-left: 4px solid #60a5fa; border-radius: 8px; background: #eff6ff; padding: 12px 14px; margin: 16px 0; }
                        .callout-title { font-weight: 600; margin-bottom: 8px; }
                        .callout-info { border-left-color: #0284c7; background: #e0f2fe; }
                        .callout-tip { border-left-color: #16a34a; background: #ecfdf5; }
                        .callout-warning { border-left-color: #d97706; background: #fffbeb; }
                        .callout-question { border-left-color: #7c3aed; background: #f5f3ff; }
                        .callout-failure { border-left-color: #dc2626; background: #fef2f2; }

                        /* ═══════ Mermaid 图表容器 ═══════ */
                        .mermaid { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin: 16px 0; }

                        /* ═══════ TOC 侧边栏样式 ═══════ */
                        /* 💡 改 TOC 宽度：修改 .toc-sidebar 的 width */
                        .toc-sidebar { position: fixed; top: 20px; right: 20px; width: 280px; max-height: calc(100vh - 40px); background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 8px 28px rgba(0,0,0,.12); overflow: hidden; z-index: 1000; transition: transform .2s ease, opacity .2s ease; }
                        .toc-sidebar.is-collapsed { transform: translateX(130%%); opacity: 0; pointer-events: none; }
                        .toc-header { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-bottom: 1px solid #eef0f3; background: #f8fafc; }
                        .toc-collapse-btn { width: 26px; height: 26px; border: none; border-radius: 50%%; background: #e2e8f0; cursor: pointer; font-size: 16px; }
                        .toc-nav { padding: 8px 8px 10px 8px; overflow: auto; max-height: calc(100vh - 100px); }
                        .toc-link { display: block; color: #334155; text-decoration: none; padding: 6px 8px; border-radius: 6px; margin: 2px 0; }
                        .toc-link:hover { background: #f1f5f9; }
                        .toc-level-2 { padding-left: 8px; font-weight: 600; }
                        .toc-level-3 { padding-left: 18px; }
                        .toc-level-4 { padding-left: 28px; color: #64748b; }
                        .toc-fab { position: fixed; left: 24px; top: 24px; width: 42px; height: 42px; border: none; border-radius: 50%%; background: #1e293b; color: #fff; font-size: 18px; box-shadow: 0 8px 24px rgba(0,0,0,.25); cursor: grab; z-index: 1001; display: none; user-select: none; }
                        .toc-fab.is-visible { display: inline-flex; align-items: center; justify-content: center; }
                        .toc-fab:active { cursor: grabbing; }
                        @media (max-width: 1080px) {
                            .toc-sidebar { width: 240px; right: 12px; top: 12px; max-height: calc(100vh - 24px); }
                        }
                    </style>
                </head>
                <body>
                    %s
                    <main class="container">
                        %s
                        %s
                    </main>
                    %s
                    %s
                </body>
                </html>
                """.formatted(title, hasToc ? tocHtml : "", metadata, htmlBody, mermaidScript, tocScript);
    }

    /**
     * 将 Front-Matter 元数据渲染为 HTML 片段（标题、创建时间、标签徽章）。
     * <p>
     * 渲染规则：
     * <ul>
     *     <li>若 title 非空 → 输出 {@code <h1 class="meta-title">}</li>
     *     <li>若 createTime 非空 → 输出 {@code <div class="meta-row">创建时间: ...}</li>
     *     <li>若 tags 非空 → 输出 {@code <span class="tag">} 徽章列表</li>
     * </ul>
     * 所有输出均经过 {@link MarkdownHtmlEngine#escapeHtml} 转义，防止 XSS。
     *
     * @param meta Front-Matter 元数据
     * @return 元数据 HTML 片段，若无有效字段则返回空字符串
     */
    private static String renderMetadata(FrontMatter meta) {
        String title = meta.title();
        String createTime = meta.createTime();
        List<String> tags = meta.tags();

        // 若所有字段均为空，直接返回空字符串，避免输出无内容的 HTML 标签
        if ((title == null || title.isBlank()) && (createTime == null || createTime.isBlank()) && tags.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append("<h1 class=\"meta-title\">").append(MarkdownHtmlEngine.escapeHtml(title)).append("</h1>");
        }
        if (createTime != null && !createTime.isBlank()) {
            sb.append("<div class=\"meta-row\">创建时间: ").append(MarkdownHtmlEngine.escapeHtml(createTime)).append("</div>");
        }
        if (!tags.isEmpty()) {
            sb.append("<div class=\"meta-row\">标签: ");
            for (String tag : tags) {
                sb.append("<span class=\"tag\">").append(MarkdownHtmlEngine.escapeHtml(tag)).append("</span>");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    // ==================== 路径工具方法 ====================

    /**
     * 计算 Markdown 文件相对于笔记根目录的 HTML 输出相对路径。
     * <p>
     * 例如：
     * <ul>
     *     <li>markdownFilePath = {@code /notes/java/并发编程.md}</li>
     *     <li>notesDir = {@code /notes}</li>
     *     <li>返回值 = {@code "java/并发编程.html"}</li>
     * </ul>
     * 返回的是正斜杠分隔的字符串（而非 Path 对象），方便直接传给
     * {@link FileStorageService#save(String, String)}。
     *
     * @param markdownFilePath 源 Markdown 文件的绝对路径
     * @param notesDir         笔记根目录的绝对路径
     * @return 相对路径字符串（扩展名已从 .md 替换为 .html）
     */
    private static String toHtmlRelativePath(Path markdownFilePath, Path notesDir) {
        // 计算相对于 notesDir 的路径（如 "java/并发编程.md"）
        Path relative = notesDir.relativize(markdownFilePath);
        // 将扩展名从 .md 替换为 .html
        String relativePath = relative.toString();
        int dotIndex = relativePath.lastIndexOf('.');
        String basePath = dotIndex == -1 ? relativePath : relativePath.substring(0, dotIndex);
        // 统一使用正斜杠分隔（兼容 Windows 和 Linux）
        return (basePath + ".html").replace('\\', '/');
    }

    /**
     * 从文件路径提取不含扩展名的文件名，作为备选标题。
     * <p>
     * 例如：{@code Path.of("java/并发编程.md")} → {@code "并发编程"}
     *
     * @param markdownPath Markdown 文件路径
     * @return 不含扩展名的文件名
     */
    private static String toTitle(Path markdownPath) {
        String filename = markdownPath.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex == -1 ? filename : filename.substring(0, dotIndex);
    }
}
