# core-node-markdown-autoconfigure

基于 [Flexmark-Java](https://github.com/vsch/flexmark-java) 构建的 **Markdown → HTML 解析引擎模块**，专为中文技术笔记（Obsidian 风格）量身打造。

采用**五层解耦架构**，内置 Spring Boot AutoConfiguration，通过配套的 `core-node-markdown-starter` 模块即可实现零代码接入。

---

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| **GFM 扩展** | 支持表格、删除线 (`~~text~~`)、任务列表 (`- [x]`) |
| **YAML Front-Matter** | 自动提取 `title`、`create_time`、`tags` 并渲染为元数据头 |
| **Obsidian WikiLink** | `[[页面]]`、`[[页面\|别名]]`、`[[页面#章节]]` 全部自动转换为 HTML 链接 |
| **Callout 块** | `> [!note]`、`> [!warning]` 等 Obsidian Callout 语法转换为带样式的语义化 div |
| **Mermaid 图表** | `` ```mermaid ``` `` 代码块自动转换为 Mermaid.js 可渲染的 div（CDN 按需注入） |
| **自动锚点 TOC** | 扫描 h2-h4 标题，自动生成可折叠的悬浮目录侧边栏（支持拖拽移动） |
| **中文 slug** | 标题锚点保留 CJK 汉字，无需担心中文链接乱码 |
| **全量扫描发布** | `LocalMarkdownScanner` 一键递归扫描 `inputDir`，批量将笔记生成为 HTML 静态站点 |
| **YAML 属性配置** | 通过 `jacolp.markdown.*` 灵活配置读取目录、输出目录，支持 IDE 自动提示 |
| **标签提取** | 提供 `extractTagsFromMarkdown` 和 `extractTagsFromHtml` 两个静态方法，转换前后均可提取 |
| **插件扩展 (Plugin)** | 通过 `MarkdownPlugin` 接口注入自定义解析规则，`@Component` 即自动生效 |

---

## 🏗️ 架构概览

本模块遵循严格的职责分离设计：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       core-node-markdown-starter                           │
│                        （零代码空壳模块，仅声明依赖）                            │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ 依赖
┌───────────────────────────────▼─────────────────────────────────────────────┐
│                  core-node-markdown-autoconfigure                          │
│                                                                             │
│  MarkdownAutoConfiguration（Spring Bean 注册 & AutoConfiguration 入口）       │
│         │                                                                   │
│         ├── MarkdownProperty      ← YAML 配置绑定（inputDir / outputDir）    │
│         ├── MarkdownHtmlEngine    ← 纯解析引擎（零 I/O，线程安全）            │
│         ├── FileStorageService    ← 存储抽象接口（可替换 OSS 等实现）          │
│         │       └── LocalFileStorageService  ← 本地文件系统默认实现           │
│         ├── MarkdownPublishService← 发布门面（引擎 → 套壳 → 存储）            │
│         └── LocalMarkdownScanner ← 全量扫描调度器（手动触发）                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 源码结构

```
core-node-framework/core-node-markdown-autoconfigure/
└── src/main/java/com/jacolp/core-node/framework/markdown/
    ├── MarkdownAutoConfiguration.java    # Spring Boot 自动配置入口
    ├── MarkdownProperty.java             # YAML 配置属性绑定（jacolp.markdown.*）
    ├── converter/
    │   ├── MarkdownHtmlEngine.java       # 核心解析引擎：Markdown → 结构化 HTML（零 I/O）
    │   ├── MarkdownPlugin.java           # 插件扩展接口：preProcess / postProcess 钩子
    │   └── MarkdownPublishService.java   # 发布门面：编排引擎 + CSS/JS 套壳 + 存储
    └── io/
        ├── FileStorageService.java       # 存储抽象接口（DIP 设计）
        ├── LocalFileStorageService.java  # 本地文件系统存储实现
        └── LocalMarkdownScanner.java     # 全量扫描调度器（手动触发）
```

**职责分离原则**：

| 类 | 职责 | 是否依赖 I/O |
|----|------|:---:|
| `MarkdownHtmlEngine` | Markdown 纯解析：Front-Matter → WikiLink → Flexmark → 后处理 → TOC | ❌ |
| `MarkdownPublishService` | 编排引擎 + HTML 套壳（CSS/JS 内联）+ 存储调度 | ❌ |
| `FileStorageService` | 存储抽象接口 | ❌ |
| `LocalFileStorageService` | 写入本地文件系统，自动创建目录 | ✅ |
| `LocalMarkdownScanner` | 递归扫描指定目录，批量驱动发布流程 | ✅ |

---

## 🚀 快速开始

### 环境要求

- Java **21+**
- Maven **3.8+**
- Spring Boot **4.x**

### 方式一：通过 Starter 零配置引入（推荐）

在你的项目 `pom.xml` 中引入 Starter 依赖：

```xml
<dependency>
    <groupId>com.jacolp</groupId>
    <artifactId>core-node-markdown-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

无需任何 `@Import` 或手动配置，Spring Boot 会自动激活所有组件。

### 方式二：非 Spring 项目中直接使用引擎

```java
// 1️⃣ 创建解析引擎（无状态，线程安全，可全局复用）
MarkdownHtmlEngine engine = new MarkdownHtmlEngine();

// 2️⃣ 解析 Markdown
HtmlProcessResult result = engine.process(rawMarkdown);

// 3️⃣ 读取结果
result.meta().title();      // 文章标题（String，可能为 null）
result.meta().tags();       // 标签列表（List<String>）
result.meta().createTime(); // 创建时间（String，可能为 null）
result.tocHtml();           // TOC 侧边栏 HTML（可能为空字符串）
result.bodyHtml();          // 处理后的 HTML 正文
```

### 方式三：完整发布流程（非 Spring 场景）

```java
// 1️⃣ 准备组件
MarkdownHtmlEngine engine = new MarkdownHtmlEngine();
FileStorageService storage = new LocalFileStorageService(Path.of("static/html"));

// 2️⃣ 创建发布服务
MarkdownPublishService publishService = new MarkdownPublishService(engine, storage);

// 3️⃣ 发布单个文件
String markdown = Files.readString(markdownPath, StandardCharsets.UTF_8);
publishService.publish(markdownPath, markdown, Path.of("static/notes"));
```

---

## ⚙️ YAML 配置属性

在 `application.yaml` 中通过 `jacolp.markdown.*` 覆盖默认值：

```yaml
jacolp:
  markdown:
    root-dir: E:/JavaProject/core-node/static      # 根目录（一般无需修改）
    input-dir: E:/JavaProject/core-node/static/notes # Markdown 源文件目录
    output-dir: E:/JavaProject/core-node/static/html # HTML 输出目录
```

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `jacolp.markdown.root-dir` | `$USER_DIR/static` | 根目录基准路径 |
| `jacolp.markdown.input-dir` | `$USER_DIR/static/notes` | Markdown 源文件扫描根目录 |
| `jacolp.markdown.output-dir` | `$USER_DIR/static/html` | HTML 文件输出根目录 |

> `$USER_DIR` 为 JVM 启动时的工作目录（`System.getProperty("user.dir")`）。
> 在父项目 `core-node` 根目录运行时，即指向 `core-node/static` 等路径。

---

## 📡 全量扫描发布（LocalMarkdownScanner）

注入 `LocalMarkdownScanner` 并手动调用 `scanAndPublishAll()`，即可一键将 `inputDir` 下所有 Markdown 递归转换为 HTML：

```java
@Autowired
private LocalMarkdownScanner scanner;

// 一键全量发布
scanner.scanAndPublishAll();
```

控制台输出示例：

```
[flexmark] 开始扫描 Markdown 文件: E:/JavaProject/core-node/static/notes
[flexmark] OK: E:/JavaProject/core-node/static/notes/java/并发编程.md
[flexmark] OK: E:/JavaProject/core-node/static/notes/redis/缓存策略.md
[flexmark] 扫描完成。成功: 2，失败: 0
```

### 三种典型调用方式

**① Controller 手动触发**
```java
@PostMapping("/publish-all")
public String publishAll() {
    scanner.scanAndPublishAll();
    return "全量发布完成";
}
```

**② CommandLineRunner 启动时执行**
```java
@Component
public class StartupPublisher implements CommandLineRunner {
    private final LocalMarkdownScanner scanner;
    public StartupPublisher(LocalMarkdownScanner scanner) { this.scanner = scanner; }

    @Override
    public void run(String... args) { scanner.scanAndPublishAll(); }
}
```

**③ Scheduled 定时任务**
```java
@Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨 3 点
public void autoPublish() { scanner.scanAndPublishAll(); }
```

---

## 🏷️ 标签提取

引擎提供两个静态方法，方便在不同阶段获取标签信息：

### 从 Markdown 源文件提取（转换前）

```java
// 无需创建引擎实例，直接静态调用
String[] tags = MarkdownHtmlEngine.extractTagsFromMarkdown(rawMarkdown);
// 例如返回 ["Java", "Spring", "并发"]
```

适用场景：文件索引、分类归档、在转换之前快速获取标签信息。

### 从 HTML 页面提取（转换后）

```java
// 从已渲染的 HTML 中反向提取标签
String[] tags = MarkdownHtmlEngine.extractTagsFromHtml(htmlContent);
```

适用场景：从数据库存储的 HTML 片段中恢复标签、对已发布的静态站点进行标签索引。

---

## 🧩 插件扩展机制 (MarkdownPlugin)

引擎内置的 WikiLink、Callout、Mermaid、TOC 等转换规则是**永远执行的兜底基石**。
在此基础上，你可以通过 `MarkdownPlugin` 接口注入自定义的解析规则——不会影响任何内置功能。

### 处理管线与插件切入点

```
原始 Markdown
     │
     ▼
╔══════════════════════════════╗
║  ① Plugin.preProcess()       ║ ← 外挂前置钩子（你的自定义规则）
╚══════════════════════════════╝
     │
     ▼
┌──────────────────────────────┐
│  ② 内置引擎核心处理（兜底）    │ ← Front-Matter / WikiLink / Flexmark / Callout / Mermaid
└──────────────────────────────┘
     │
     ▼
╔══════════════════════════════╗
║  ③ Plugin.postProcess()      ║ ← 外挂后置钩子（你的最终修正）
╚══════════════════════════════╝
     │
     ▼
最终 HTML 输出
```

### 实战示例：Obsidian 图片嵌入 → 数据库查 URL

假设你的笔记中使用 `![[xxx.jpg]]` 语法嵌入图片，而图片的实际 URL 存储在数据库中：

```java
@Component  // 打上此注解即可被引擎自动发现
public class ImageLinkPlugin implements MarkdownPlugin {

    private final ImageRepository imageRepo;

    public ImageLinkPlugin(ImageRepository imageRepo) {
        this.imageRepo = imageRepo;
    }

    @Override
    public String preProcess(String rawMarkdown) {
        Pattern pattern = Pattern.compile("!\\[\\[([^]]+)]]");
        Matcher matcher = pattern.matcher(rawMarkdown);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String fileName = matcher.group(1);
            String url = imageRepo.findUrlByName(fileName);
            matcher.appendReplacement(sb,
                Matcher.quoteReplacement("<img src=\"" + url + "\" alt=\"" + fileName + "\">"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
```

> 💡 整个过程零配置：只要实现 `MarkdownPlugin` 并标注 `@Component`，
> `MarkdownAutoConfiguration` 会通过 `ObjectProvider` 自动收集所有插件 Bean 并注入引擎。

### 非 Spring 项目中使用插件

```java
MarkdownPlugin myPlugin = new MyCustomPlugin();
MarkdownHtmlEngine engine = new MarkdownHtmlEngine(List.of(myPlugin));
HtmlProcessResult result = engine.process(rawMarkdown);
```

### 多插件排序

在 Spring Boot 中，多个插件的执行顺序由 `@Order` 注解控制（值越小越先执行）：

```java
@Component
@Order(1)  // 先执行
public class ImageLinkPlugin implements MarkdownPlugin { ... }

@Component
@Order(2)  // 后执行
public class CustomSyntaxPlugin implements MarkdownPlugin { ... }
```

---

## 🔌 自定义存储实现

`FileStorageService` 接口只有一个方法，扩展极其简单：

```java
public interface FileStorageService {
    void save(String relativePath, String content);
}
```

### 示例：替换为阿里云 OSS 存储

```java
public class AliyunOssStorageService implements FileStorageService {

    private final OSSClient ossClient;
    private final String bucketName;
    private final String prefix;  // 如 "blog/html/"

    public AliyunOssStorageService(OSSClient ossClient, String bucketName, String prefix) {
        this.ossClient = ossClient;
        this.bucketName = bucketName;
        this.prefix = prefix;
    }

    @Override
    public void save(String relativePath, String content) {
        String objectKey = prefix + relativePath;
        ossClient.putObject(bucketName, objectKey,
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }
}
```

然后在你的 `@Configuration` 中自定义 Bean（得益于 `@ConditionalOnMissingBean`，默认本地实现会自动让位）：

```java
@Bean
public FileStorageService fileStorageService() {
    return new AliyunOssStorageService(ossClient, "my-bucket", "blog/html/");
}
```

---

## 🎨 CSS 样式自定义

所有页面样式集中在 `MarkdownPublishService.wrapHtml()` 的 `<style>` 块中，按需修改。

### 常用改动速查表

| 想改什么 | 在哪里找 | 示例 |
|----------|----------|------|
| 页面背景色 | `body` 的 `background` | `#f7f8fa` → `#1a1a2e`（深色主题） |
| 正文字体 | `body` 的 `font-family` | 加入 `"Noto Sans SC"` |
| 正文区域宽度 | `.container` 的 `max-width` | `960px` → `1200px` |
| 代码块主题色 | `pre` 的 `background` / `color` | `#0f172a` → `#282c34`（One Dark） |
| 标签徽章颜色 | `.tag` 的 `background` / `color` | `#eef2ff` → `#fce7f3`（粉色） |
| Callout 样式 | `.callout-xxx` 系列 | 复制一行改颜色即可新增类型 |
| TOC 侧边栏宽度 | `.toc-sidebar` 的 `width` | `280px` → `320px` |
| Mermaid CDN 版本 | `mermaidScript` 中的 `@11` | `@11` → `@10` |

### 新增 Callout 类型示例

在 `<style>` 块追加一行：

```css
.callout-success { border-left-color: #10b981; background: #d1fae5; }
```

对应 Markdown 写法：

```markdown
> [!success] 成功
> 操作已完成。
```

---

## 📝 Markdown 格式参考

### YAML Front-Matter

```yaml
---
title: 我的文章标题
create_time: 2026-04-04
tags:
  - Java
  - 并发
---
```

> 若不写 Front-Matter，页面标题自动回退为文件名。

### Obsidian WikiLink

```markdown
[[另一篇文章]]                     → 链接到 另一篇文章.html
[[另一篇文章|点我跳转]]             → 自定义显示文本
[[另一篇文章#某个章节]]              → 跳转到对应页面的锚点
[[#当前页面的某个章节]]              → 当前页面内锚点跳转
```

### Callout 块

```markdown
> [!note] 注意
> 这里是 Note 型 Callout 的内容。

> [!warning] 警告
> 这里是警告内容，会渲染为橙色边框。

> [!tip]
> 不写标题时，自动使用类型名首字母大写（如 "Tip"）。
```

支持的类型：`note`、`info`、`tip`、`warning`、`question`、`failure`（可自由扩展 CSS）

### Mermaid 图表

````markdown
```mermaid
graph TD;
    A[开始] --> B[处理];
    B --> C[结束];
```
````

---

## ⚙️ 依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `spring-boot-starter-parent` | 4.0.5 | 基础构建环境 |
| `com.vladsch.flexmark:flexmark-all` | 0.64.8 | Markdown 解析与渲染 |

---

## 📋 已知限制与建议扩展

> [!NOTE]
> 以下是目前的已知边界条件，可按需扩展。

- **重复标题锚点**：若同一页面出现同名标题，生成的 `id` 会重复。后续可在 `addHeadingIds` 中加入计数去重逻辑。
- **Callout 嵌套**：当前正则不支持 Callout 内嵌套 Callout 的场景。
- **WikiLink 路径**：跨目录的 WikiLink（如 `[[子目录/页面]]`）目前不做路径解析，生成的 `.html` 链接为扁平化格式，需注意相对路径一致性。
- **CSS 外置**：所有样式均内联于 `wrapHtml`，如需统一管理，建议后续提取为独立的 `.css` 静态文件引用。
