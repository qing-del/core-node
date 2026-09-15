package com.jacolp.framework.markdown.io;

import com.jacolp.framework.markdown.converter.MarkdownPublishService;
import com.jacolp.framework.markdown.MarkdownAutoConfiguration;
import com.jacolp.framework.markdown.MarkdownProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 本地 Markdown 文件全量扫描与发布调度器。
 * <p>
 * 负责从 {@link MarkdownProperty#getInputDir()} 指定的目录中递归扫描所有 Markdown 文件
 * （{@code .md} 和 {@code .markdown} 后缀），并逐一交给 {@link MarkdownPublishService} 完成
 * "解析 → 套壳 → 存储"的完整发布流程。
 *
 * <h3>核心方法</h3>
 * <p>
 * 本类只有一个公开方法：{@link #scanAndPublishAll()}。
 * 调用它即可一键将整个 notes 目录转换为 HTML 静态站点。
 * </p>
 *
 * <h3>⚠️ 重要：本类不会自动运行</h3>
 * <p>
 * 为了安全起见，本类<strong>不会</strong>在 Spring Boot 启动时自动执行扫描。
 * 您需要在合适的时机手动调用它。以下是三种常见的调用方式：
 * </p>
 *
 * <h4>方式一：在 Controller 中手动触发</h4>
 * <pre>{@code
 * @RestController
 * public class NoteController {
 *
 *     private final LocalMarkdownScanner scanner;
 *
 *     public NoteController(LocalMarkdownScanner scanner) {
 *         this.scanner = scanner;
 *     }
 *
 *     @PostMapping("/publish-all")
 *     public String publishAll() {
 *         scanner.scanAndPublishAll();
 *         return "全量发布完成";
 *     }
 * }
 * }</pre>
 *
 * <h4>方式二：在 CommandLineRunner 中启动时执行（适合 CLI 工具）</h4>
 * <pre>{@code
 * @Component
 * public class StartupPublisher implements CommandLineRunner {
 *
 *     private final LocalMarkdownScanner scanner;
 *
 *     public StartupPublisher(LocalMarkdownScanner scanner) {
 *         this.scanner = scanner;
 *     }
 *
 *     @Override
 *     public void run(String... args) {
 *         scanner.scanAndPublishAll();
 *     }
 * }
 * }</pre>
 *
 * <h4>方式三：在定时任务中周期执行（适合自动化场景）</h4>
 * <pre>{@code
 * @Component
 * public class ScheduledPublisher {
 *
 *     private final LocalMarkdownScanner scanner;
 *
 *     public ScheduledPublisher(LocalMarkdownScanner scanner) {
 *         this.scanner = scanner;
 *     }
 *
 *     @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨 3 点执行
 *     public void autoPublish() {
 *         scanner.scanAndPublishAll();
 *     }
 * }
 * }</pre>
 *
 * <h3>Spring Boot 装配方式</h3>
 * <p>
 * 本类通过 {@link MarkdownAutoConfiguration}
 * 中的 {@code @Bean} 方法自动注册。只要项目引入了
 * {@code middleware-markdown-starter} 依赖，就可以直接
 * {@code @Autowired} 注入使用，无需任何额外配置。
 * </p>
 */
public class LocalMarkdownScanner {

    /** 发布服务门面（负责单文件的解析 → 套壳 → 存储） */
    private final MarkdownPublishService publishService;

    /** 配置属性（提供 inputDir 扫描路径） */
    private final MarkdownProperty property;

    /**
     * 构造扫描器实例。
     * <p>
     * 由 {@link com.jacolp.MarkdownAutoConfiguration} 自动装配，
     * 注入 {@link MarkdownPublishService} 和 {@link MarkdownProperty}。
     *
     * @param publishService 发布服务门面
     * @param property       配置属性（用于获取 inputDir）
     */
    public LocalMarkdownScanner(MarkdownPublishService publishService, MarkdownProperty property) {
        this.publishService = publishService;
        this.property = property;
    }

    /**
     * 一键扫描并发布全部 Markdown 文件。
     * <p>
     * 执行流程：
     * <ol>
     * <li>根据 {@link MarkdownProperty#getInputDir()} 定位到 Markdown 源文件目录</li>
     * <li>递归遍历目录下所有 {@code .md} 和 {@code .markdown} 文件</li>
     * <li>逐一读取文件内容，调用 {@link MarkdownPublishService#publish} 完成转换</li>
     * <li>统计成功/失败数量并打印到控制台</li>
     * </ol>
     *
     * <p>
     * 单个文件转换失败不会中断整体流程——错误会被捕获并打印到 {@code System.err}，
     * 其余文件继续正常转换。
     * </p>
     */
    public void scanAndPublishAll() {
        Path inputDir = Path.of(property.getInputDir());

        // 校验输入目录是否存在
        if (!Files.isDirectory(inputDir)) {
            System.err.println("[flexmark] 输入目录不存在，跳过扫描: " + inputDir);
            return;
        }

        System.out.println("[flexmark] 开始扫描 Markdown 文件: " + inputDir);

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        // 递归遍历目录，筛选 .md 和 .markdown 后缀的文件
        try (Stream<Path> files = Files.walk(inputDir)) {
            files.filter(Files::isRegularFile)
                 .filter(path -> {
                     String name = path.getFileName().toString().toLowerCase();
                     return name.endsWith(".md") || name.endsWith(".markdown");
                 })
                 .forEach(markdownPath -> {
                     try {
                         // 读取源文件内容
                         String rawMarkdown = Files.readString(markdownPath, StandardCharsets.UTF_8);

                         // 调用发布服务完成：解析 → 套壳 → 存储
                         publishService.publish(markdownPath, rawMarkdown, inputDir);

                         success.incrementAndGet();
                         System.out.println("[flexmark] OK: " + markdownPath);
                     } catch (Exception e) {
                         // 单个文件失败不中断整体流程
                         failed.incrementAndGet();
                         System.err.println("[flexmark] FAIL: " + markdownPath);
                         e.printStackTrace(System.err);
                     }
                 });
        } catch (IOException exception) {
            throw new UncheckedIOException("遍历输入目录失败: " + inputDir, exception);
        }

        // 输出扫描报告
        System.out.printf("[flexmark] 扫描完成。成功: %d，失败: %d%n", success.get(), failed.get());
    }
}
