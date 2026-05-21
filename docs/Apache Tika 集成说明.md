# Apache Tika 集成说明

本文档说明了项目中集成 Apache Tika 进行文档解析的实现。

## 📚 概述

Apache Tika 是一个内容分析工具包，可以从各种格式的文档中检测并提取文本和元数据。我们使用 Tika 来替代简单的文件读取，实现对多种文档格式的支持。

## 🔧 集成内容

### 1. Maven 依赖

在 `pom.xml` 中添加了以下依赖：

```xml
<!-- Apache Tika - 文档解析库 -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.1</version>
</dependency>

<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.1</version>
</dependency>
```

### 2. 文档解析服务

创建了 [`DocumentParserService.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/DocumentParserService.java)

## 📄 支持的文档格式

| 类别 | 格式 | 说明 |
|------|------|------|
| **文本** | TXT, MD, CSV | 纯文本文件 |
| **文档** | DOC, DOCX | Microsoft Word |
| **表格** | XLS, XLSX | Microsoft Excel |
| **演示** | PPT, PPTX | Microsoft PowerPoint |
| **PDF** | PDF | Adobe PDF |
| **Web** | HTML, HTM | 网页文件 |
| **其他** | JSON, XML, RTF | 结构化文本 |
| **办公** | ODT, ODS, ODP | OpenDocument |
| **电子书** | EPUB | 电子书格式 |
| **邮件** | EML, MSG | 邮件格式 |

## 🔍 核心功能

### 1. 文档解析

```java
@Autowired
private DocumentParserService documentParserService;

// 解析文档
String content = documentParserService.parseDocument(
    inputStream,    // 文件输入流
    filename        // 文件名
);
```

### 2. 格式验证

```java
// 检查是否支持该格式
boolean supported = documentParserService.isSupported("document.pdf");
```

### 3. MIME 类型检测

```java
// 检测文档的 MIME 类型
String mimeType = documentParserService.detectMediaType(inputStream, filename);
```

## 📝 文本分块策略

使用智能分块策略，优先保持语义完整性：

### 分块规则

1. **段落分割**：按双换行符分割成段落
2. **长度判断**：
   - 50-500 字符：直接作为一个块
   - < 50 字符：与相邻段落合并
   - > 500 字符：继续按句子分割
3. **句子分割**：按中文句号、英文句号、感叹号、问号分割
4. **最终分块**：每个块限制在 50-500 字符之间

### 分块示例

**输入**：
```
RAG是检索增强生成技术。

它结合了检索系统和生成式AI的优势。
非常适合知识库问答场景。
```

**输出**：
```
[
  "RAG是检索增强生成技术。",
  "它结合了检索系统和生成式AI的优势。",
  "非常适合知识库问答场景。"
]
```

## 🔄 集成到文档处理流程

### 之前（简单文本读取）
```java
private void processDocument(Document document) {
    // 直接读取文件
    String content = new String(Files.readAllBytes(Paths.get(document.getFilePath())));
    String[] chunks = chunkContent(content);
    // ...
}
```

### 现在（Apache Tika 解析）
```java
@Autowired
private DocumentParserService documentParserService;

private void processDocument(Document document) {
    // 使用 Tika 解析
    String content = documentParserService.parseDocument(
        Files.newInputStream(Paths.get(document.getFilePath())),
        document.getTitle()
    );

    // 智能分块
    List<String> chunks = smartChunkContent(content);
    // ...
}
```

## ⚙️ 配置说明

### 最大文本长度

```java
// 默认 10MB
private static final int DEFAULT_MAX_TEXT_LENGTH = 10 * 1024 * 1024;
```

如需处理更大的文档，可以在 `DocumentParserService` 中调整此值。

### 文本清理

解析后的文本会经过以下处理：
- 移除零宽字符和控制字符
- 规范化换行符（\r\n → \n）
- 合并多个连续空行
- 移除行首行尾多余空白

## 🎯 使用示例

### 上传文档

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@/path/to/document.pdf" \
  -F "knowledgeBaseId=YOUR_KB_ID"
```

### 自动处理的文档格式

上传以下任意格式的文档，都会自动解析并分块：

- ✅ `员工手册.docx`
- ✅ `产品介绍.pdf`
- ✅ `会议记录.xlsx`
- ✅ `技术文档.md`
- ✅ `API文档.html`
- ✅ `数据报表.csv`

## 🐛 故障排查

### 解析失败

如果文档解析失败，检查：

1. **文件是否损坏**：尝试用其他软件打开
2. **文件是否加密**：解密后再上传
3. **文件是否过大**：检查 DEFAULT_MAX_TEXT_LENGTH 配置
4. **日志查看**：查看应用日志中的具体错误信息

### 不支持的格式

如果提示 "不支持的文件格式"，检查：
- 文件扩展名是否在支持列表中
- 文件扩展名是否正确（大写/小写不影响）

## 📈 性能考虑

- **Tika 解析速度**：取决于文档大小和复杂度
- **大文档处理**：建议文档大小不超过 50MB
- **并发处理**：Spring 默认线程池处理上传

## 🔮 未来优化方向

1. **异步处理**：使用消息队列异步解析大文档
2. **增量解析**：支持文档更新时的增量解析
3. **元数据提取**：提取文档标题、作者、创建时间等元数据
4. **多语言支持**：优化中文文档的分块策略

## 📚 参考资料

- [Apache Tika 官方文档](https://tika.apache.org/)
- [Tika 1.x 文档](https://cwiki.apache.org/confluence/display/TIKA/TikaServer)
- [支持的文档格式](https://tika.apache.org/2.9.1/formats.html)
