# RAG 知识库 - 文档处理完整流程

本文档基于当前 `rag-search-service` 的实际代码，详细描述从文档上传、Apache Tika 解析、智能文本分块、阿里云 DashScope 向量化处理到 pgvector 入库的完整流程。

## 📚 目录

1. [总体流程架构](#总体流程架构)
2. [步骤详解](#步骤详解)
   - [① 文档上传入口（Controller）](#1️⃣-文档上传入口controller)
   - [② 文档保存与格式校验（DocumentService）](#2️⃣-文档保存与格式校验documentservice)
   - [③ 异步处理调度（DocumentProcessService）](#3️⃣-异步处理调度documentprocessservice)
   - [④ 文档内容解析（DocumentParserService）](#4️⃣-文档内容解析documentparserservice)
   - [⑤ 智能文本分块（Smart Chunking）](#5️⃣-智能文本分块smart-chunking)
   - [⑥ 向量化处理（VectorService）](#6️⃣-向量化处理vectorservice)
   - [⑦ 向量入库（DocumentChunkVectorMapper）](#7️⃣-向量入库documentchunkvectormapper)
   - [⑧ 状态更新与完成](#8️⃣-状态更新与完成)
3. [核心代码文件清单](#核心代码文件清单)
4. [数据模型](#数据模型)
5. [数据库表结构](#数据库表结构)
6. [错误处理与容错机制](#错误处理与容错机制)
7. [时序图](#时序图)
8. [快速测试](#快速测试)

---

## 总体流程架构

```
┌─────────────────┐
│  HTTP Multipart  │  POST /api/documents/upload
│  file + kbId     │  ───────────────────────────────┐
└─────────────────┘                                    │
                                                       ▼
                                             ┌─────────────────┐
                                  ┌──────────│ DocumentController │
                                  │          └────────┬────────┘
                                  │                   │ uploadDocument()
                                  │                   ▼
                                  │          ┌─────────────────┐
                                  │          │ DocumentService  │  ① 验证知识库 + 格式
                                  │          │                  │  ② 文件存盘 + DB记录
                                  │          └────────┬────────┘
                                  │                   │ processDocument()
                                  │                   ▼          ↓ 异步线程池
                                  │          ┌─────────────────────────┐
                                  │          │ DocumentProcessService  │  @Async
                                  │          │                         │
                                  │          │  ③ 状态 → PROCESSING    │
                                  │          │  ④ 调 DocumentParser    │
                                  │          │     → Apache Tika 解析  │
                                  │          │  ⑤ 智能分块(smartChunk) │
                                  │          │  ⑥ 逐块调 VectorService │
                                  │          │     → DashScope 向量化  │
                                  │          │  ⑦ INSERT INTO pgvector │
                                  │          │  ⑧ 状态 → INDEXED       │
                                  │          └─────────────────────────┘
                                  ▼
                        ┌─────────────────┐
                        │  API 立即返回   │
                        │  "上传成功"     │
                        └─────────────────┘
```

| 阶段 | 服务类 | 核心技术 |
|------|--------|---------|
| ① 入口 | `DocumentController` | Spring MVC `@PostMapping` + Multipart |
| ② 存储校验 | `DocumentService` | `@Transactional` + MyBatis-Plus |
| ③ 异步调度 | `DocumentProcessService` | `@Async("documentProcessExecutor")` |
| ④ 内容解析 | `DocumentParserService` | Apache Tika + ZIP 回退 |
| ⑤ 智能分块 | `DocumentProcessService.smartChunkContent()` | 段落/句子/固定长度三级策略 |
| ⑥ 向量化 | `VectorService.embed()` | 阿里云 DashScope `text-embedding-v4` |
| ⑦ 入库 | `DocumentChunkVectorMapper` | MyBatis `@Insert` + pgvector `CAST` |

---

## 步骤详解

### 1️⃣ 文档上传入口（Controller）

**接口**：`POST /api/documents/upload`

| 参数 | 类型 | 说明 |
|------|------|------|
| `file` | MultipartFile | 上传的文档文件 |
| `knowledgeBaseId` | UUID | 目标知识库 ID |

**权限**：`@PreAuthorize("hasAnyRole('ADMIN', 'KNOWLEDGE_BASE_ADMIN')")`

**代码位置**：[`DocumentController.java:28-37`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/controller/DocumentController.java#L28-L37)

```java
@PostMapping(value="/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam("knowledgeBaseId") UUID knowledgeBaseId) throws IOException {
    UUID userId = getCurrentUserId();  // 从 JWT Token 提取
    DocumentResponse response = documentService.uploadDocument(file, knowledgeBaseId, userId);
    return ResponseEntity.ok(success("上传成功", response));
}
```

**返回值**：立即返回 `DocumentResponse`（此时 `status=UPLOADING`），后续处理由异步线程池完成，用户可通过 `GET /api/documents/{id}` 轮询状态。

---

### 2️⃣ 文档保存与格式校验（DocumentService）

**代码位置**：[`DocumentService.java:42-80`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/DocumentService.java#L42-L80)

整个 `uploadDocument()` 方法在 `@Transactional` 事务中执行，确保文档记录要么完整写入，要么全部回滚。

#### 步骤拆解

**A. 验证知识库存在**

```java
if (knowledgeBaseMapper.selectById(knowledgeBaseId) == null) {
    throw new ResourceNotFoundException("知识库", "id", knowledgeBaseId.toString());
}
```

**B. 格式校验**

```java
if (!documentParserService.isSupported(originalFilename)) {
    throw new BusinessException("不支持的文件格式");
}
```

`isSupported()` 检查文件扩展名是否在白名单中。**支持的格式**：

| 类别 | 扩展名 |
|------|--------|
| 纯文本 | `txt`, `md`, `markdown`, `csv`, `log`, `json`, `xml`, `html`, `htm` |
| Word | `doc`, `docx` |
| PDF | `pdf` |
| Excel | `xls`, `xlsx` |
| PowerPoint | `ppt`, `pptx` |
| OpenDocument | `odt`, `ods`, `odp` |
| 富文本 | `rtf` |
| 其他 | `eml`, `msg`, `epub` |

**C. 文件存盘**

```java
String storedFilename = UUID.randomUUID().toString() + "." + fileExtension;
Path filePath = uploadPath.resolve(storedFilename);   // ./uploads/documents/{uuid}.{ext}
Files.copy(file.getInputStream(), filePath);
```

使用 UUID 重命名避免文件名冲突，存储路径为 `./uploads/documents/`。

**D. 创建数据库记录**

```java
Document document = Document.builder()
        .id(UUID.randomUUID())
        .title(originalFilename.replace("." + fileExtension, ""))
        .filePath(filePath.toString())
        .fileType(fileExtension)
        .fileSize((int) file.getSize())
        .knowledgeBaseId(knowledgeBaseId)
        .uploadedBy(userId)
        .status(Document.Status.UPLOADING)   // ← 初始状态
        .build();
documentMapper.insert(document);
```

**E. 触发异步处理**

```java
documentProcessService.processDocument(document);
```

注意：此调用**不阻塞** HTTP 请求返回。`processDocument()` 标注了 `@Async`，在独立线程池中执行。

---

### 3️⃣ 异步处理调度（DocumentProcessService）

**代码位置**：[`DocumentProcessService.java:58-116`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/DocumentProcessService.java#L58-L116)

**线程池配置**：[`AsyncConfig.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/config/AsyncConfig.java)

```java
@Bean("documentProcessExecutor")
public Executor documentProcessExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);         // 核心线程数
    executor.setMaxPoolSize(4);          // 最大线程数
    executor.setQueueCapacity(100);      // 等待队列容量
    executor.setThreadNamePrefix("doc-process-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
}
```

**异步处理状态机**：

```
UPLOADING ──→ PROCESSING ──→ INDEXED (成功)
                          └─→ FAILED (异常/空内容)
```

**方法签名**：

```java
@Async("documentProcessExecutor")
public void processDocument(Document document) {
```

---

### 4️⃣ 文档内容解析（DocumentParserService）

**代码位置**：[`DocumentParserService.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/DocumentParserService.java)

这是一个三层解析策略的服务：

#### 解析策略决策树

```
文件名检测
    │
    ├─ 纯文本格式 (.txt/.md/.csv/.json 等)
    │   └─ 直接读取为 UTF-8 字符串
    │
    └─ 其他格式 (.pdf/.docx/.epub 等)
        │
        ├─ 主策略：Apache Tika AutoDetectParser
        │   │  BodyContentHandler + Metadata + ParseContext
        │   │  自动检测 MIME 类型 → 选取对应 Parser → 提取文本
        │   └─ 成功 → 返回文本
        │   └─ 失败/空内容 ↓
        │
        └─ 回退策略：ZIP 归档提取
            └─ 适用于 epub/docx/xlsx/odt 等 ZIP-based 格式
            └─ 用 ZipInputStream 遍历归档条目
            └─ 提取 .html/.htm/.xhtml/.xml/.txt 文件内容
            └─ 去除 HTML 标签（replaceAll("<[^>]+>", " ")）
```

#### Apache Tika 解析

```java
private String parseWithTika(InputStream inputStream, String filename) {
    BodyContentHandler handler = new BodyContentHandler(DEFAULT_MAX_TEXT_LENGTH); // 10MB 上限
    Metadata metadata = new Metadata();
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
    ParseContext context = new ParseContext();
    parser.parse(inputStream, handler, metadata, context);
    String content = cleanText(handler.toString());
    return content;
}
```

#### ZIP 回退解析

```java
private String parseZipArchiveAsText(String filePath) {
    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(filePath)))) {
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.getName().matches(".*\\.(html|htm|xhtml|xml|txt)$")) {
                String text = baos.toString(StandardCharsets.UTF_8);
                text = text.replaceAll("<[^>]+>", " ");  // 去除 HTML 标签
                allText.append(text);
            }
        }
    }
    return cleanText(allText.toString());
}
```

#### 文本清洗

```java
private String cleanText(String text) {
    text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", ""); // 去除控制字符
    text = text.replaceAll("\\r\\n|\\r", "\n");                             // 规范换行符
    text = text.replaceAll("\\n{3,}", "\n\n");                              // 合并连续空行
    text = text.replaceAll("[ \\t]+\n", "\n");                              // 去除行尾空格
    text = text.replaceAll("\\n[ \\t]+", "\n");                             // 去除行首空格
    return text.trim();
}
```

---

### 5️⃣ 智能文本分块（Smart Chunking）

**代码位置**：[`DocumentProcessService.java:142-205`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/DocumentProcessService.java#L142-L205)

分块目标：每个 chunk 在 **50 ~ 500 字符**之间，保证单个 chunk 在语义上自包含且长度适中，适合作为向量检索的基本单元。

#### 三级分块策略

```
全文文本
    │
    ├─ 按连续空行（2+ 个换行）分割为段落
    │   content.split("\\n{2,}")
    │
    ├─ 情况 A：段落长度 50~500 字符
    │   └─ 直接作为一个 chunk ✅
    │
    ├─ 情况 B：段落 < 50 字符（标题、短行）
    │   ├─ 有前一个 chunk → 尝试合并
    │   │   ├─ 合并后 ≤500 → 合并 ✅
    │   │   └─ 合并后 >500 → 各自独立保存
    │   └─ 无前一个 chunk → 单独保存
    │
    └─ 情况 C：段落 > 500 字符
        ├─ 第一步：按标点符号拆分为句子
        │   split("(?<=[。！？.!?])\\s*")
        │   └─ 无标点时 → 按 200 字符固定切分（降级）
        └─ 第二步：句子聚合成 ≤500 字符的 chunk
            └─ 累积到接近 500 → 切分保存
```

#### 句子拆分（降级策略）

```java
private List<String> splitIntoSentences(String text) {
    // 正向回顾断言：在中英文句末标点之后切分
    String[] parts = text.split("(?<=[。！？.!?])\\s*");

    // 无任何标点时，按 200 字符固定切分
    if (sentences.isEmpty()) {
        for (int i = 0; i < text.length(); i += 200) {
            sentences.add(text.substring(i, Math.min(i + 200, text.length())));
        }
    }
    return sentences;
}
```

---

### 6️⃣ 向量化处理（VectorService）

**代码位置**：[`VectorService.java:77-95`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/VectorService.java#L77-L95)

#### 配置

在 `application.yml` 中配置阿里云 DashScope：

```yaml
dashscope:
  api-key: sk-xxxx
  embedding:
    model: text-embedding-v4
    dimensions: 1536
```

#### 向量生成（带降级）

```java
public float[] embed(String text) {
    try {
        // 主策略：调用阿里云 DashScope text-embedding-v4 API
        float[] result = embeddingModel.embed(text);
        log.debug("Generated embedding via Alibaba text-embedding-v4, dimension={}", result.length);
        return result;
    } catch (Exception e) {
        // 降级策略：API 不可用时使用基于 hash 的伪随机向量
        log.warn("Embedding API failed, using fallback mock embedding: {}", e.getMessage());
        return generateFallbackEmbedding(text);
    }
}

private float[] generateFallbackEmbedding(String text) {
    // 使用 sin(hash * 0.01) 生成 1536 维伪随机向量
    // 相同文本始终生成相同向量（可重复性）
    float[] embedding = new float[1536];
    int hash = text != null ? text.hashCode() : 0;
    for (int i = 0; i < 1536; i++) {
        embedding[i] = (float) (Math.sin(i * hash * 0.01) * 0.5 + 0.5);
    }
    return embedding;
}
```

**降级策略特点**：
- ✅ 不依赖外部 API，保证系统可用性
- ✅ 相同文本生成相同向量
- ⚠️ 语义区分度远低于真实 Embedding 模型

---

### 7️⃣ 向量入库（DocumentChunkVectorMapper）

**代码位置**：[`DocumentChunkVectorMapper.java:15-21`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/mapper/DocumentChunkVectorMapper.java#L15-L21)

#### 向量格式转换

```java
// float[1536] → pgvector 字符串格式
String arrayToString(float[] array) {
    // 输出示例：[0.123, 0.456, 0.789, ...]
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < array.length; i++) {
        if (i > 0) sb.append(", ");
        sb.append(array[i]);
    }
    sb.append("]");
    return sb.toString();
}
```

#### SQL 插入

```java
@Insert("INSERT INTO document_chunk (id, document_id, chunk_index, content, embedding, created_at) " +
        "VALUES (#{id}, #{documentId}, #{chunkIndex}, #{content}, CAST(#{embedding} AS vector), NOW())")
void insertWithVector(@Param("id") UUID id,
                      @Param("documentId") UUID documentId,
                      @Param("chunkIndex") int chunkIndex,
                      @Param("content") String content,
                      @Param("embedding") String embedding);
```

关键点：`CAST(#{embedding} AS vector)` 将字符串格式的数组转换为 PostgreSQL pgvector 类型。

#### 向量检索（扩展说明）

入库后的向量通过**余弦相似度**检索（`<=>` 运算符）：

```sql
SELECT dc.id, dc.document_id, dc.chunk_index, dc.content,
       1 - (dv.embedding <=> CAST(#{queryVector} AS vector)) AS similarity
FROM document_chunk dc
LEFT JOIN document_chunk_vector dv ON dc.id = dv.id
LEFT JOIN documents d ON dc.document_id = d.id
<where>
    <if test="knowledgeBaseId != null">
        d.knowledge_base_id = #{knowledgeBaseId}
    </if>
</where>
ORDER BY similarity DESC
LIMIT #{limit}
```

---

### 8️⃣ 状态更新与完成

处理链路中的每个阶段都会更新 `document.status` 字段：

| 时机 | 状态 | 含义 |
|------|------|------|
| `DocumentService.uploadDocument()` 创建记录时 | `UPLOADING` | 文件已存盘，等待异步处理 |
| `DocumentProcessService.processDocument()` 开始时 | `PROCESSING` | 解析/分块/向量化进行中 |
| 解析内容为空时 | `FAILED` | 文档无可提取文本 |
| 全部 chunk 向量化入库完成时 | `INDEXED` | 可被检索和问答 ✅ |
| 任何阶段抛异常 | `FAILED` | 处理出错 ❌ |

前端可通过轮询 `GET /api/documents/{id}` 获取实时状态。

---

## 核心代码文件清单

| 文件 | 路径 | 职责 |
|------|------|------|
| `DocumentController` | `controller/DocumentController.java` | HTTP 接口，Multipart 接收，JWT 鉴权 |
| `DocumentService` | `service/DocumentService.java` | 格式校验、文件存盘、DB 记录、触发异步处理 |
| `DocumentProcessService` | `service/DocumentProcessService.java` | `@Async` 异步处理：解析→分块→向量化→入库 |
| `DocumentParserService` | `service/DocumentParserService.java` | Apache Tika 解析 + ZIP 回退 + 文本清洗 |
| `VectorService` | `service/VectorService.java` | 嵌入向量生成（DashScope） + 格式转换 + 入库 |
| `AsyncConfig` | `config/AsyncConfig.java` | 异步线程池配置（2-4 线程，队列 100） |
| `Document` | `entity/Document.java` | 文档实体，MyBatis-Plus `@TableName("documents")` |
| `DocumentChunk` | `entity/DocumentChunk.java` | 分块实体，`@TableName("document_chunk")` |
| `DocumentChunkMapper` | `mapper/DocumentChunkMapper.java` | 分块增删查（LambdaQuery） |
| `DocumentChunkVectorMapper` | `mapper/DocumentChunkVectorMapper.java` | 向量插入 + 余弦/L2/内积检索 |
| `DocumentChunkVectorMapper.xml` | `resources/mapper/DocumentChunkVectorMapper.xml` | 检索 SQL（JOIN documents 按知识库过滤） |
| `application.yml` | `resources/application.yml` | DashScope API Key、pgvector 配置、文件上传限制 |

---

## 数据模型

### Document（文档实体）

**表名**：`documents`（MyBatis-Plus 映射）

```java
@TableName("documents")
public class Document {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;            // 主键，自动生成 UUID

    private String title;       // 文档标题（取原始文件名，去掉扩展名）
    private String filePath;    // 磁盘路径，如 ./uploads/documents/{uuid}.pdf
    private String fileType;    // 扩展名，如 pdf, docx, txt
    private Integer fileSize;   // 文件大小（字节）
    private String metadata;    // 预留的 JSON 元数据字段
    private UUID knowledgeBaseId;  // 所属知识库 ID
    private UUID uploadedBy;       // 上传用户 ID

    private Status status;      // UPLOADING → PROCESSING → INDEXED / FAILED

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;    // MyBatis-Plus 自动填充

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;    // MyBatis-Plus 自动填充

    public enum Status { UPLOADING, PROCESSING, INDEXED, FAILED }
}
```

### DocumentChunk（分块实体）

**表名**：`document_chunk`

```java
@TableName("document_chunk")
public class DocumentChunk {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;            // 主键，自动生成 UUID

    private UUID documentId;    // 所属文档 ID
    private Integer chunkIndex; // 块序号（0-based，排序用）
    private String content;     // 文本内容（≤500 字符）
    private String embedding;   // pgvector 格式的向量字符串

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

---

## 数据库表结构

### documents 表

```sql
CREATE TABLE documents (
    id               UUID PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    file_path        VARCHAR(500),
    file_type        VARCHAR(50),
    file_size        INTEGER,
    metadata         TEXT,
    knowledge_base_id UUID REFERENCES knowledge_base(id),
    uploaded_by       UUID REFERENCES users(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### document_chunk 表

```sql
CREATE TABLE document_chunk (
    id          UUID PRIMARY KEY,
    document_id UUID REFERENCES documents(id),
    chunk_index INTEGER NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1536),            -- pgvector 扩展
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 错误处理与容错机制

整个处理链路中设计了多层次容错：

| 环节 | 错误场景 | 处理方式 |
|------|---------|---------|
| 文件接收 | 知识库不存在 | 抛出 `ResourceNotFoundException`，HTTP 404 |
| 文件接收 | 不支持的格式 | 抛出 `BusinessException`，HTTP 400 |
| 文件接收 | 磁盘写入失败 | `IOException` 向上传播，事务回滚 |
| Tika 解析 | 解析异常 | 返回空字符串，进入 ZIP 回退 |
| Tika 解析 | 返回空结果 | 进入 ZIP 回退 |
| ZIP 回退 | 不是 ZIP 格式 | 返回空字符串 |
| 内容提取 | 提取文本为空 | `document.status = FAILED`，不阻塞 |
| Embedding API | DashScope 不可用 | 降级为 hash 伪随机向量 |
| 整体处理 | 任何未捕获异常 | `document.status = FAILED`，记录日志 |
| 事务 | DB 插入失败 | `@Transactional` 自动回滚 |

---

## 时序图

```
Client          Controller       DocumentService    ProcessService    ParserService     VectorService       DB
  │                 │                   │                  │                │                 │               │
  │ POST /upload    │                   │                  │                │                 │               │
  │ ───────────────>│                   │                  │                │                 │               │
  │                 │ uploadDocument()  │                  │                │                 │               │
  │                 │ ────────────────> │                  │                │                 │               │
  │                 │                   │ 验证知识库       │                │                 │               │
  │                 │                   │ ──────────────────────────────────────────────────────────────> │
  │                 │                   │                  │                │                 │               │
  │                 │                   │ 格式校验         │                │                 │               │
  │                 │                   │ ────────────────>│ isSupported()  │                 │               │
  │                 │                   │                  │                │                 │               │
  │                 │                   │ 文件存盘         │                │                 │               │
  │                 │                   │ ────→ ./uploads/ │                │                 │               │
  │                 │                   │                  │                │                 │               │
  │                 │                   │ INSERT document  │                │                 │               │
  │                 │                   │ (status=UPLOAD)  │                │                 │               │
  │                 │                   │ ──────────────────────────────────────────────────────────────> │
  │                 │                   │                  │                │                 │               │
  │                 │                   │ processDocument()│                │                 │               │
  │                 │                   │ ────────────────>│                │                 │               │
  │                 │                   │                  │ @Async 启动    │                 │               │
  │                 │                   │                  │                │                 │               │
  │  ← 200 "上传成功"                  │                  │                │                 │               │
  │                 │                   │                  │                │                 │               │
  │                 │                   │    HTTP 请求已返回（不等待异步）    │                 │               │
  │                 │                   │                  │                │                 │               │
  │                 │                   │                  │ 状态→PROCESSING│                 │               │
  │                 │                   │                  │ ──────────────────────────────────────────> │
  │                 │                   │                  │                │                 │               │
  │                 │                   │                  │ parseDocument()│                 │               │
  │                 │                   │                  │ ──────────────>│                 │               │
  │                 │                   │                  │                │ Tika 解析       │               │
  │                 │                   │                  │                │ / ZIP 回退      │               │
  │                 │                   │                  │ <──────────────│ 返回文本        │               │
  │                 │                   │                  │                │                 │               │
  │                 │                   │                  │ smartChunk()    │                 │               │
  │                 │                   │                  │ → List<String>  │                 │               │
  │                 │                   │                  │                │                 │               │
  │                 │                   │                  │ 遍历每个 chunk  │                 │               │
  │                 │                   │                  │                │                 │               │
  │                 │                   │                  │  embed(chunk)   │                 │               │
  │                 │                   │                  │ ──────────────────────────────>│               │
  │                 │                   │                  │                │ DashScope API   │               │
  │                 │                   │                  │ <──────────────────────────────│ float[1536]    │
  │                 │                   │                  │                │                 │               │
  │                 │                   │                  │ saveChunk()     │                 │               │
  │                 │                   │                  │ ──────────────────────────────>│ INSERT ...    │
  │                 │                   │                  │                │                 │ CAST(AS vec)  │
  │                 │                   │                  │                │                 │ ─────────────>│
  │                 │                   │                  │                │                 │               │
  │                 │                   │                  │ 循环结束        │                 │               │
  │                 │                   │                  │                │                 │               │
  │                 │                   │                  │ 状态→INDEXED    │                 │               │
  │                 │                   │                  │ ──────────────────────────────────────────> │
```

---

## 快速测试

### 上传文档

```bash
# 1. 先登录获取 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser2024","password":"123456"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 2. 上传文档（PDF/Word/TXT 等）
curl -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/your/document.pdf" \
  -F "knowledgeBaseId=cea53e8d-3fda-4797-9fdb-fe03c5a5bc19"

# 3. 查询文档状态（轮询直到 status=INDEXED）
curl -s http://localhost:8080/api/documents/{DOC_ID} \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

### 查看分块结果

```sql
SELECT chunk_index, content, embedding
FROM document_chunk
WHERE document_id = 'YOUR_DOC_ID'
ORDER BY chunk_index;
```

### 验证向量检索

```sql
-- 用相同文本的向量检索相似 chunk
SELECT dc.id, dc.chunk_index, dc.content,
       1 - (dv.embedding <=> (
           SELECT embedding FROM document_chunk
           WHERE document_id = 'YOUR_DOC_ID' ORDER BY chunk_index LIMIT 1
       )) AS similarity
FROM document_chunk dc
JOIN document_chunk_vector dv ON dc.id = dv.id
ORDER BY similarity DESC
LIMIT 5;
```
