# RAG 文档处理流程 - 快速参考卡

## 🚀 核心流程

```
文档上传 → 文本分块 → 向量化处理 → 数据入库
```

---

## 📋 详细步骤

### 1️⃣ 文档上传

**服务类**: `DocumentService.uploadDocument()`  
**Controller**: `DocumentController`  
**实体**: `Document`

**关键点**:
- 验证知识库存在
- 文件保存到 `./uploads/documents/`
- 文件名使用 UUID
- 状态: `UPLOADING` → `PROCESSING` → `INDEXED`

### 2️⃣ 文本分块

**方法**: `DocumentService.chunkContent()`  
**实体**: `DocumentChunk`

**分块规则**:
- 英文句号后跟空格: `. `
- 中文句号后跟空格: `。 `
- 双换行符: `\n\n`

### 3️⃣ 向量化处理

**服务**: `VectorService.generateMockEmbedding()`  
**维度**: `1536` 维  
**模型**: text-embedding-ada-002 (当前为模拟)

### 4️⃣ 数据入库

**数据库**: PostgreSQL + pgvector  
**表名**: `document_chunk`  
**向量字段**: `embedding vector(1536)`

**向量格式**: `[0.1234, 0.5678, ...]`

---

## 📊 数据模型

### Document 实体

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `title` | String | 标题 |
| `filePath` | String | 存储路径 |
| `fileType` | String | 文件类型 |
| `fileSize` | Integer | 文件大小 |
| `knowledgeBaseId` | UUID | 知识库ID |
| `uploadedBy` | UUID | 上传用户 |
| `status` | Status | UPLOADING / PROCESSING / INDEXED / FAILED |

### DocumentChunk 实体

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `documentId` | UUID | 文档ID |
| `chunkIndex` | Integer | 块序号 |
| `content` | TEXT | 内容 |
| `embedding` | vector(1536) | 向量嵌入 |
| `createdAt` | TIMESTAMP | 创建时间 |

---

## 🔧 关键代码路径

### 文件上传
```
DocumentController.uploadDocument()
  ↓
DocumentService.uploadDocument()
  ↓
DocumentService.processDocument()
  ↓
DocumentService.chunkContent()
  ↓
VectorService.generateMockEmbedding()
  ↓
VectorService.saveDocumentChunk()
```

### 代码文件位置

| 文件 | 路径 |
|------|------|
| DocumentService | [`backend/src/main/java/com/example/rag/service/DocumentService.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/DocumentService.java) |
| VectorService | [`backend/src/main/java/com/example/rag/service/VectorService.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/service/VectorService.java) |
| DocumentController | [`backend/src/main/java/com/example/rag/controller/DocumentController.java`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/src/main/java/com/example/rag/controller/DocumentController.java) |
| 完整流程文档 | [`backend/docs/DOCUMENT_PROCESSING_WORKFLOW.md`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/backend/docs/DOCUMENT_PROCESSING_WORKFLOW.md) |

---

## 🎯 快速测试

### 上传文档
```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@/path/to/document.txt" \
  -F "knowledgeBaseId=YOUR_KB_ID"
```

### 查询文档块
```sql
SELECT id, chunk_index, content, embedding
FROM document_chunk
WHERE document_id = 'YOUR_DOC_ID'
ORDER BY chunk_index;
```

---

## 📝 状态流转

```
UPLOADING (上传中)
    ↓
PROCESSING (处理中)
    ↓
INDEXED (已索引) ✅
    或
FAILED (失败) ❌
```

---

## ⚙️ 配置

- **上传目录**: `./uploads/documents/`
- **向量维度**: `1536`
- **数据库**: PostgreSQL + pgvector
- **向量字段类型**: `vector(1536)`
