# RAG 知识库 - 文档处理完整流程

本文档详细描述了从文档上传、文本分块、向量化处理到最终入库的完整流程。

## 📚 目录

1. [总体流程架构](#总体流程架构)
2. [步骤详解](#步骤详解)
3. [核心代码参考](#核心代码参考)
4. [数据模型说明](#数据模型说明)
5. [时序图](#时序图)

---

## 总体流程架构

### 流程图

```
┌─────────────┐     ┌──────────────┐     ┌──────────────────┐     ┌───────────────┐
│  文档上传   │ ──▶│  文本分块    │ ──▶│  向量化处理     │ ──▶│  数据入库    │
│  (Document) │     │  (Chunker)   │     │  (Vectorizer)   │     │  (PostgreSQL) │
└─────────────┘     └──────────────┘     └──────────────────┘     └───────────────┘

   ↓                    ↓                      ↓                       ↓
  保存元数据          分段处理               生成向量嵌入            存储向量数据
```

### 处理步骤

| 阶段 | 说明 | 主要实体 | 服务类 |
|------|------|---------|--------|
| 1. 文档上传 | 接收上传的文件，保存元数据到数据库 | `Document` | `DocumentService` |
| 2. 文本分块 | 读取文件内容，按规则分割成块 | `DocumentChunk` | `DocumentService` |
| 3. 向量化处理 | 为每个文本块生成向量嵌入 | `float[1536]` | `VectorService` |
| 4. 数据入库 | 将文本块和向量存储到 PostgreSQL + pgvector | `DocumentChunk` | `VectorService` |

---

## 步骤详解

### 1️⃣ 文档上传

#### 详细步骤

1. **验证知识库存在**：检查目标知识库是否存在
2. **创建上传目录**：如果不存在则创建 `./uploads/documents/` 目录
3. **生成存储文件名**：使用 UUID 生成唯一文件名，避免冲突
4. **保存文件到磁盘**：将上传的文件保存到指定路径
5. **创建文档记录**：保存文档元数据到数据库
6. **更新状态**：设置文档状态为 `UPLOADING`
7. **触发后续处理**：调用 `processDocument()` 进行后续处理

#### 文档状态流转

```
UPLOADING (上传中)
    ↓
PROCESSING (处理中)
    ↓
INDEXED (已索引/完成) ✅

或

FAILED (处理失败) ❌
```

#### 相关代码

**主要方法**: [`DocumentService.uploadDocument()`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/DocumentService.java#L48)

```java
@Transactional
public DocumentResponse uploadDocument(MultipartFile file, UUID knowledgeBaseId, UUID userId) throws IOException {
    // 1. 验证知识库存在
    if (!knowledgeBaseRepository.existsById(knowledgeBaseId)) {
        throw new ResourceNotFoundException("知识库", "id", knowledgeBaseId);
    }

    // 2. 创建上传目录
    Path uploadPath = Paths.get(UPLOAD_DIR);
    if (!Files.exists(uploadPath)) {
        Files.createDirectories(uploadPath);
    }

    // 3. 生成存储文件名
    String originalFilename = file.getOriginalFilename();
    String fileExtension = getFileExtension(originalFilename);
    String storedFilename = UUID.randomUUID().toString() + "." + fileExtension;
    Path filePath = uploadPath.resolve(storedFilename);

    // 4. 保存文件到磁盘
    Files.copy(file.getInputStream(), filePath);

    // 5. 创建文档实体
    Document document = Document.builder()
            .title(originalFilename != null ? originalFilename.replace("." + fileExtension, "") : "Untitled")
            .filePath(filePath.toString())
            .fileType(fileExtension)
            .fileSize((int) file.getSize())
            .knowledgeBaseId(knowledgeBaseId)
            .uploadedBy(userId)
            .status(Document.Status.UPLOADING)
            .build();

    // 6. 保存文档记录
    Document savedDocument = documentRepository.save(document);
    
    // 7. 触发后续处理
    processDocument(savedDocument);

    return DocumentResponse.fromEntity(savedDocument);
}
```

#### 文档实体 (`Document`)

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 文档ID（主键） |
| `title` | String | 文档标题 |
| `filePath` | String | 文件存储路径 |
| `fileType` | String | 文件类型（扩展名） |
| `fileSize` | Integer | 文件大小（字节） |
| `knowledgeBaseId` | UUID | 所属知识库ID |
| `uploadedBy` | UUID | 上传用户ID |
| `status` | Status | 状态：UPLOADING、PROCESSING、INDEXED、FAILED |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |

---

### 2️⃣ 文本分块

#### 详细步骤

1. **更新状态为处理中**：将文档状态设置为 `PROCESSING`
2. **读取文件内容**：从磁盘读取上传的文件
3. **文本分段**：按分隔符分割文本
4. **遍历处理每个块**：为非空块进行向量化处理
5. **更新状态为已索引**：处理完成后设置为 `INDEXED`

#### 分块策略

目前使用正则表达式进行分块：

```java
private String[] chunkContent(String content) {
    return content.split("(?<=\\.\\s|。\\s|\\n\\n)");
}
```

**分块规则**:
- 英文句号后跟空格：`. `
- 中文句号后跟空格：`。 `
- 双换行符：`\n\n`

**示例**:
```
输入文本:
RAG 是检索增强生成技术。它结合了检索和生成两种能力。
非常适合知识库问答场景。

输出分段:
["RAG 是检索增强生成技术。", "它结合了检索和生成两种能力。", "\n", "非常适合知识库问答场景。"]
```

#### 相关代码

**主要方法**: [`DocumentService.processDocument()`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/DocumentService.java#L110)

```java
private void processDocument(Document document) {
    // 1. 更新状态为处理中
    document.setStatus(Document.Status.PROCESSING);
    documentRepository.save(document);

    try {
        // 2. 读取文件内容
        String content = new String(Files.readAllBytes(Paths.get(document.getFilePath())));
        
        // 3. 文本分段
        String[] chunks = chunkContent(content);
        int chunkIndex = 0;
        for (String chunk : chunks) {
            if (!chunk.trim().isEmpty()) {
                // 4. 为每个块进行向量化和入库
                float[] embedding = vectorService.generateMockEmbedding(chunk);
                vectorService.saveDocumentChunk(document.getId(), chunkIndex, chunk, embedding);
                chunkIndex++;
            }
        }
        
        log.info("Document processed: {} chunks created for document {}", chunkIndex, document.getId());
        
        // 5. 更新状态为已索引
        document.setStatus(Document.Status.INDEXED);
        documentRepository.save(document);
    } catch (Exception e) {
        log.error("Failed to process document: {}", document.getId(), e);
        document.setStatus(Document.Status.FAILED);
        documentRepository.save(document);
    }
}
```

---

### 3️⃣ 向量化处理

#### 详细步骤

1. **调用向量化服务**：传入文本块获取向量
2. **生成向量**：将文本转换为 1536 维的向量数组
3. **格式转换**：将 `float[]` 转换为 pgvector 格式的字符串

#### 当前实现（模拟向量）

目前使用数学函数生成伪随机向量：

```java
public float[] generateMockEmbedding(String text) {
    float[] embedding = new float[VECTOR_DIMENSION]; // 1536维
    int hash = text.hashCode();
    for (int i = 0; i < VECTOR_DIMENSION; i++) {
        // 使用正弦函数生成伪随机向量，确保相同文本生成相同向量
        embedding[i] = (float) (Math.sin(i * hash * 0.01) * 0.5 + 0.5);
    }
    return embedding;
}
```

**特点**:
- ✅ 相同文本生成相同向量（可重复性）
- ✅ 不需要外部 API 调用
- ⚠️ 仅用于演示，实际效果不佳

#### 改进方向：使用真实嵌入模型

**集成 OpenAI text-embedding-ada-002**:

```java
// 实际生产环境应该这样实现
@Service
public class RealVectorService {
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    public float[] generateEmbedding(String text) {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> body = new HashMap<>();
        body.put("model", "text-embedding-ada-002");
        body.put("input", text);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        
        ResponseEntity<OpenAIEmbeddingResponse> response = 
            restTemplate.postForEntity(
                "https://api.openai.com/v1/embeddings",
                request,
                OpenAIEmbeddingResponse.class
            );
        
        return response.getBody().getData().get(0).getEmbedding();
    }
}
```

#### 相关代码

**主要方法**: [`VectorService.generateMockEmbedding()`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/VectorService.java#L157)

---

### 4️⃣ 数据入库

#### 详细步骤

1. **向量格式转换**：将 `float[1536]` 转换为 pgvector 格式字符串
2. **构造 SQL**：使用原生 SQL 插入向量数据
3. **执行插入**：将文本块和向量存储到 PostgreSQL
4. **验证入库**：记录日志

#### 向量格式

**pgvector 格式**:
```
[0.1234, 0.5678, 0.9012, ...]
```

**格式转换方法**:
```java
private String arrayToString(float[] array) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (int i = 0; i < array.length; i++) {
        if (i > 0) {
            sb.append(", ");
        }
        sb.append(array[i]);
    }
    sb.append("]");
    return sb.toString();
}
```

#### 数据库表结构

**document_chunk 表**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 块ID（主键） |
| `document_id` | UUID | 文档ID（外键） |
| `chunk_index` | Integer | 块序号（排序用） |
| `content` | TEXT | 文本内容 |
| `embedding` | vector(1536) | 向量嵌入（pgvector 类型） |
| `created_at` | TIMESTAMP | 创建时间 |

#### 相关代码

**主要方法**: [`VectorService.saveDocumentChunk()`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/VectorService.java#L48)

```java
@Transactional
public void saveDocumentChunk(UUID documentId, int chunkIndex, String content, float[] embedding) {
    // 1. 将float数组转换为pgvector格式的字符串
    String vectorString = arrayToString(embedding);
    
    // 2. 使用原生SQL插入向量数据
    String sql = "INSERT INTO document_chunk (id, document_id, chunk_index, content, embedding, created_at) " +
                 "VALUES (:id, :documentId, :chunkIndex, :content, CAST(:embedding AS vector), NOW())";
    
    entityManager.createNativeQuery(sql)
            .setParameter("id", UUID.randomUUID())
            .setParameter("documentId", documentId)
            .setParameter("chunkIndex", chunkIndex)
            .setParameter("content", content)
            .setParameter("embedding", vectorString)
            .executeUpdate();

    log.debug("Saved document chunk: documentId={}, chunkIndex={}", documentId, chunkIndex);
}
```

---

## 核心代码参考

### 主要服务类

| 服务类 | 文件路径 | 说明 |
|--------|---------|------|
| `DocumentService` | [`backend/src/main/java/com/example/rag/service/DocumentService.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/DocumentService.java) | 文档上传和处理 |
| `VectorService` | [`backend/src/main/java/com/example/rag/service/VectorService.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/VectorService.java) | 向量生成和存储 |
| `DocumentController` | [`backend/src/main/java/com/example/rag/controller/DocumentController.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/controller/DocumentController.java) | 文档上传接口 |

### 控制器接口

**上传文档**:
```
POST /api/documents/upload
Content-Type: multipart/form-data

参数:
- file: 上传的文件
- knowledgeBaseId: 知识库ID

Header:
- Authorization: Bearer {token}
```

---

## 数据模型说明

### Document（文档实体）

**表名**: `documents`

```java
@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String title;
    private String filePath;
    private String fileType;
    private Integer fileSize;
    
    @Column(columnDefinition = "text")
    private String metadata;
    
    private UUID knowledgeBaseId;
    private UUID uploadedBy;
    
    @Enumerated(EnumType.STRING)
    private Status status;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public enum Status {
        UPLOADING,   // 上传中
        PROCESSING,  // 处理中
        INDEXED,     // 已索引
        FAILED       // 失败
    }
}
```

### DocumentChunk（文档块实体）

**表名**: `document_chunk`

```java
@Entity
@Table(name = "document_chunk")
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private UUID documentId;
    private Integer chunkIndex;
    
    @Column(columnDefinition = "text")
    private String content;
    
    @Column(columnDefinition = "vector(1536)")
    private String embedding;
    
    private LocalDateTime createdAt;
}
```

---

## 时序图

### 文档上传流程

```
┌───────────┐       ┌─────────────────┐      ┌───────────────────┐      ┌────────────┐
│  Client   │       │ DocumentController│      │ DocumentService  │      │ Postgres   │
└─────┬─────┘       └────────┬────────┘      └─────────┬─────────┘      └─────┬──────┘
      │                      │                        │                       │
      │ POST /upload         │                        │                       │
      │ ────────────────────>│                        │                       │
      │                      │ uploadDocument()       │                       │
      │                      │ ──────────────────────>│                       │
      │                      │                        │ 验证知识库存在         │
      │                      │                        │ ─────────────────────>│
      │                      │                        │                       │
      │                      │                        │ 保存文件到磁盘         │
      │                      │                        │                       │
      │                      │                        │ 创建 Document          │
      │                      │                        │ ─────────────────────>│
      │                      │                        │                       │
      │                      │                        │ processDocument()     │
      │                      │                        │ ────────────────────┐│
      │                      │                        │ 读取文件内容         ││
      │                      │                        │ 文本分块             ││
      │                      │                        │ ↓循环处理每个块       ││
      │                      │                        │   生成向量           ││
      │                      │                        │   保存 DocumentChunk  ││
      │                      │                        │   ─────────────────>││
      │                      │                        │ 更新状态为 INDEXED   ││
      │                      │                        │ ─────────────────────>│
      │                      │                        │                       │
      │                      │ 返回 DocumentResponse  │                       │
      │ <────────────────────│ <──────────────────────│                       │
      │                      │                        │                       │
```

### 详细处理时序

```
DocumentController
       │
       │ uploadDocument(MultipartFile, UUID, UUID)
       │ ─────────────────────────────────────────────────────────────┐
       │                                                               │
       │                                                       DocumentService
       │                                                               │
       │                                                               ├─ 验证知识库是否存在
       │                                                               │
       │                                                               ├─ 创建上传目录
       │                                                               │
       │                                                               ├─ 生成存储文件名
       │                                                               │
       │                                                               ├─ 保存文件到磁盘
       │                                                               │
       │                                                               ├─ 创建 Document 实体
       │                                                               │
       │                                                               ├─ 保存 Document 到数据库
       │                                                               │
       │                                                               └─ processDocument()
       │                                                                       │
       │                                                                       │
       │                                                                       ├─ 更新状态为 PROCESSING
       │                                                                       │
       │                                                                       ├─ 读取文件内容
       │                                                                       │
       │                                                                       ├─ 文本分块 chunkContent()
       │                                                                       │
       │                                                                       ├─ ↓ 循环处理每个块
       │                                                                       │     │
       │                                                                       │     ├─ 跳过空块
       │                                                                       │     │
       │                                                                       │     └─ VectorService
       │                                                                       │          │
       │                                                                       │          ├─ generateMockEmbedding()
       │                                                                       │          │
       │                                                                       │          └─ saveDocumentChunk()
       │                                                                       │               │
       │                                                                       │               ├─ 转换向量格式
       │                                                                       │               │
       │                                                                       │               └─ 保存到数据库
       │                                                                       │
       │                                                                       └─ 更新状态为 INDEXED
       │                                                                               │
       │  DocumentResponse  │                                 │
       │ <─────────────────────────────────────────────────────────────────────────────────┘
       │
```

---

## 总结

### 当前流程特点

✅ **完整的处理链路**：文档上传 → 文本分块 → 向量化 → 入库  
✅ **状态跟踪**：清晰的状态流转，便于监控  
✅ **向量存储**：支持 pgvector 向量数据库  
✅ **事务管理**：使用 @Transactional 保证数据一致性  

### 可优化方向

1. **更好的分块策略**：支持按语义、固定大小等策略
2. **异步处理**：使用消息队列进行异步处理
3. **真实向量模型**：集成 OpenAI、Cohere 等嵌入 API
4. **分块元数据**：保存更多分块信息（位置、引用等）
5. **处理进度**：实时反馈处理进度
6. **批量处理**：优化大批量场景的性能

---

## 快速测试

### 使用 curl 上传文档

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@/path/to/document.txt" \
  -F "knowledgeBaseId=YOUR_KB_ID"
```

### 查看文档状态

```bash
curl -X GET http://localhost:8080/api/documents/YOUR_DOC_ID \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 查询向量化块

```sql
SELECT id, chunk_index, content, embedding
FROM document_chunk
WHERE document_id = 'YOUR_DOC_ID'
ORDER BY chunk_index;
```
