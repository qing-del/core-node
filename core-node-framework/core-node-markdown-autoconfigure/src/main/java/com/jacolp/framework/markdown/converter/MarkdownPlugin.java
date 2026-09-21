package com.jacolp.framework.markdown.converter;

/**
 * Markdown 解析引擎扩展插件接口。
 * <p>
 * 允许外部项目在不修改引擎源码的前提下，注入自定义的文本转换规则。
 * 插件通过两个生命周期钩子（Hook）介入引擎的处理管线：
 * </p>
 *
 * <h3>处理管线与插件切入点</h3>
 * <pre>{@code
 *  原始 Markdown
 *       │
 *       ▼
 *  ╔══════════════════════════════╗
 *  ║  ① Plugin.preProcess()       ║ ← 【外挂前置钩子】您的自定义规则在这里拦截
 *  ╚══════════════════════════════╝
 *       │
 *       ▼
 *  ┌──────────────────────────────┐
 *  │  ② 内置引擎核心处理（兜底）    │ ← Front-Matter / WikiLink / Flexmark / Callout / Mermaid / TOC
 *  └──────────────────────────────┘
 *       │
 *       ▼
 *  ╔══════════════════════════════╗
 *  ║  ③ Plugin.postProcess()      ║ ← 【外挂后置钩子】您的自定义修正在这里补充
 *  ╚══════════════════════════════╝
 *       │
 *       ▼
 *  最终 HTML 输出
 * }</pre>
 *
 * <h3>核心设计原则</h3>
 * <ul>
 *     <li>插件是"外挂"，不是"替代品"。即使没有任何插件注册，引擎内置的
 *         表格、WikiLink、Callout、Mermaid、TOC 等核心转换规则依然正常运行。</li>
 *     <li>多个插件按注册顺序依次执行（链式调用），前一个插件的输出是后一个插件的输入。</li>
 *     <li>两个方法均提供了 {@code default} 实现（直接返回原文），
 *         您只需覆写您关心的钩子即可。</li>
 * </ul>
 *
 * <h3>典型使用场景</h3>
 * <p>
 * 例如，在您的博客系统中，Obsidian 笔记可能包含图片嵌入语法 {@code ![[xxx.jpg]]}，
 * 而图片的实际 URL 存储在数据库中。此时可以编写一个插件：
 * </p>
 * <pre>{@code
 * @Component  // 打上 @Component 注解即可自动注入引擎
 * public class ImageLinkPlugin implements MarkdownPlugin {
 *
 *     private final ImageRepository imageRepo;
 *
 *     public ImageLinkPlugin(ImageRepository imageRepo) {
 *         this.imageRepo = imageRepo;
 *     }
 *
 *     @Override
 *     public String preProcess(String rawMarkdown) {
 *         // 用正则匹配 ![[xxx.jpg]]，去数据库查 URL 并替换
 *         Pattern pattern = Pattern.compile("!\\[\\[([^]]+)]]");
 *         Matcher matcher = pattern.matcher(rawMarkdown);
 *         StringBuilder sb = new StringBuilder();
 *         while (matcher.find()) {
 *             String fileName = matcher.group(1);
 *             String url = imageRepo.findUrlByName(fileName);
 *             matcher.appendReplacement(sb,
 *                 Matcher.quoteReplacement("<img src=\"" + url + "\" alt=\"" + fileName + "\">"));
 *         }
 *         matcher.appendTail(sb);
 *         return sb.toString();
 *     }
 * }
 * }</pre>
 *
 * <h3>Spring Boot 集成方式</h3>
 * <p>
 * 在 Spring Boot 项目中，只需让实现类标注 {@code @Component} 或通过
 * {@code @Bean} 方法注册，{@link com.jacolp.MarkdownAutoConfiguration}
 * 会自动将容器中所有 {@code MarkdownPlugin} 类型的 Bean 收集并注入到引擎中。
 * </p>
 *
 * <h3>非 Spring 项目中使用</h3>
 * <pre>{@code
 * MarkdownPlugin myPlugin = new MyCustomPlugin();
 * MarkdownHtmlEngine engine = new MarkdownHtmlEngine(List.of(myPlugin));
 * }</pre>
 *
 * @see MarkdownHtmlEngine
 * @see com.jacolp.MarkdownAutoConfiguration
 */
public interface MarkdownPlugin {

    /**
     * 前置处理钩子：在引擎核心处理之前拦截原始 Markdown 文本。
     * <p>
     * 您可以在此方法中对原始文本进行任意修改，修改后的文本将作为后续
     * 引擎内置处理（Front-Matter 提取、WikiLink 替换、Flexmark 渲染等）的输入。
     * <p>
     * 典型用途：
     * <ul>
     *     <li>替换自定义语法（如 {@code ![[xxx.jpg]]} → {@code <img src="...">}）</li>
     *     <li>注入额外的 Markdown 内容（如自动追加脚注）</li>
     *     <li>过滤或清理不需要的文本片段</li>
     * </ul>
     *
     * @param rawMarkdown 原始 Markdown 文本（可能已经过前序插件处理）
     * @return 处理后的 Markdown 文本；若无需修改，直接返回原文
     */
    default String preProcess(String rawMarkdown) {
        return rawMarkdown;
    }

    /**
     * 后置处理钩子：在引擎核心处理全部完成后拦截最终 HTML。
     * <p>
     * 此时 HTML 已经经过了完整的内置处理管线（包括 Callout、Mermaid、TOC 等），
     * 您可以在此方法中对成品 HTML 进行最后的修正或增强。
     * <p>
     * 典型用途：
     * <ul>
     *     <li>注入自定义的 CSS class 或 data 属性</li>
     *     <li>替换特定的 HTML 标签结构</li>
     *     <li>添加额外的 JavaScript 片段</li>
     * </ul>
     *
     * @param htmlBody 引擎处理后的 HTML 正文（可能已经过前序插件处理）
     * @return 处理后的 HTML 正文；若无需修改，直接返回原文
     */
    default String postProcess(String htmlBody) {
        return htmlBody;
    }
}
