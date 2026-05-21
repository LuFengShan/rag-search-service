# RAG 知识库 - 问答处理完整流程

本文档基于 `rag-search-service` 实际代码，详细描述 `/api/qa/question` 问答接口的完整处理链路，以及 Spring AI 框架在本项目中的使用方式。

---

## 📚 目录

1. [问答接口总体架构](#问答接口总体架构)
2. [步骤详解](#步骤详解)
   - [① 请求入口（QAController）](#1️⃣-请求入口qacontroller)
   - [② 问题持久化（QAService - 步骤 1）](#2️⃣-问题持久化qaservice---步骤-1)
   - [③ 问题向量化（AgentService → VectorService.embed）](#3️⃣-问题向量化agentservice--vectorserviceembed)
   - [④ 向量相似度检索（VectorService → pgvector）](#4️⃣-向量相似度检索vectorservice--pgvector)
   - [⑤ 构建 Prompt（AgentService - SystemMessage + UserMessage）](#5️⃣-构建-promptagentservice---systemmessage--usermessage)
   - [⑥ 调用 DeepSeek 大模型（ChatModel.call）](#6️⃣-调用-deepseek-大模型chatmodelcall)
   - [⑦ 答案持久化（QAService - 步骤 2-5）](#7️⃣-答案持久化qaservice---步骤-2-5)
   - [⑧ 返回响应](#8️⃣-返回响应)
   - [⑨ 对话历史查询](#9️⃣-对话历史查询)
3. [Spring AI 框架详解](#spring-ai-框架详解)
   - [3.1 什么是 Spring AI](#31-什么是-spring-ai)
   - [3.2 本项目的依赖配置](#32-本项目的依赖配置)
   - [3.3 ChatModel —— 聊天模型](#33-chatmodel--聊天模型)
   - [3.4 EmbeddingModel —— 嵌入模型](#34-embeddingmodel--嵌入模型)
   - [3.5 Prompt / Message —— 提示词体系](#35-prompt--message--提示词体系)
   - [3.6 application.yml 配置详解](#36-applicationyml-配置详解)
   - [3.7 Spring AI 可移植性原理](#37-spring-ai-可移植性原理)
   - [3.8 常见问题与最佳实践](#38-常见问题与最佳实践)
4. [核心代码文件清单](#核心代码文件清单)
5. [数据模型](#数据模型)
6. [时序图](#时序图)
7. [快速测试](#快速测试)

---

## 问答接口总体架构

```
POST /api/qa/question
{
  "question": "请假流程是什么？",
  "knowledgeBaseId": "cea53e8d-..."  // 可选
}
     │
     ▼
┌──────────────────────────────────────────────────────────────┐
│                     QAController                              │
│  • JWT 鉴权 → getCurrentUserId()                             │
│  • 参数校验 @Valid                                            │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                      QAService                                │
│  @Transactional                                               │
│                                                               │
│  ① INSERT question (status=PENDING)                           │
│  ② agentService.answer(question, knowledgeBaseId)             │
│  ③ INSERT answer (answer + sources JSON + confidence)         │
│  ④ UPDATE question (status=ANSWERED)                          │
│  ⑤ return AnswerResponse                                      │
└──────────────────────────────┬───────────────────────────────┘
                               │ ②
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                     AgentService (RAG 核心)                    │
│                                                               │
│  1. vectorService.embed(question)                             │
│     └─ 阿里云 DashScope text-embedding-v4 → float[1536]       │
│                                                               │
│  2. vectorService.searchByCosineSimilarity(embedding, kb, 5)  │
│     └─ pgvector <=> 余弦相似度 → top-5 DocumentChunk          │
│                                                               │
│  3. buildContext(chunks) → 拼接检索上下文                      │
│     buildSources(chunks) → 构建引用来源列表                     │
│                                                               │
│  4. new Prompt([                                               │
│       SystemMessage("你是企业知识库助手..."),                   │
│       UserMessage("检索内容 + 用户问题...")                     │
│     ])                                                        │
│                                                               │
│  5. chatModel.call(prompt)                                    │
│     └─ DeepSeek deepseek-chat (via OpenAI-compatible API)     │
│                                                               │
│  6. return AnswerResponse(answer, sources, confidence)         │
└──────────────────────────────────────────────────────────────┘
```

| 阶段 | 服务类 | 核心技术 |
|------|--------|---------|
| ① 入口 | `QAController` | JWT 鉴权、`@Valid` 校验 |
| ② 编排 | `QAService` | `@Transactional`、问题→答案双表写入 |
| ③ 向量化 | `VectorService.embed()` | 阿里云 DashScope `text-embedding-v4`（1536 维） |
| ④ 检索 | `VectorService.searchByCosineSimilarity()` | pgvector `<=>` 余弦相似度 |
| ⑤ Prompt 构建 | `AgentService` | Spring AI `Prompt` / `SystemMessage` / `UserMessage` |
| ⑥ LLM 调用 | `AgentService` → `ChatModel` | DeepSeek `deepseek-chat`（OpenAI 兼容 API） |

---

## 步骤详解

### 1️⃣ 请求入口（QAController）

**接口**：`POST /api/qa/question`

**请求体**（`QuestionRequest`）：
```json
{
  "question": "请假流程是什么？",
  "knowledgeBaseId": "cea53e8d-3fda-4797-9fdb-fe03c5a5bc19"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | String | ✅ | 用户输入的自然语言问题，`@NotBlank` 校验 |
| `knowledgeBaseId` | UUID | ❌ | 限定检索的知识库 ID，传 `null` 则全局检索所有知识库 |

**权限**：`@PreAuthorize("isAuthenticated()")` —— 任意已登录用户

**代码**：[`QAController.java:28-36`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/controller/QAController.java#L28-L36)

```java
@PostMapping("/question")
public ResponseEntity<ApiResponse<AnswerResponse>> askQuestion(
        @Valid @RequestBody QuestionRequest request) {
    UUID userId = getCurrentUserId();        // 从 JWT Token 提取当前用户 ID
    AnswerResponse response = qaService.askQuestion(request, userId);
    return ResponseEntity.ok(success(response));
}
```

**`getCurrentUserId()` 的实现**（[`BaseController.java:20-26`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/controller/BaseController.java#L20-L26)）：

```java
protected UUID getCurrentUserId() {
    Authentication auth = getCurrentAuthentication();
    if (auth != null && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
        return userDetails.getUserId();  // JWT Token 中的 sub 字段
    }
    return null;
}
```

---

### 2️⃣ 问题持久化（QAService - 步骤 1）

**代码**：[`QAService.java:122-134`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/QAService.java#L122-L134)

```java
Question question = Question.builder()
        .id(UUID.randomUUID())                         // MyBatis-Plus ASSIGN_UUID 自动生成
        .userId(userId)                                // 当前登录用户
        .question(request.getQuestion())               // 用户原始问题文本
        .knowledgeBaseId(request.getKnowledgeBaseId()) // 可选，限定知识库
        .status(Question.Status.PENDING)               // 初始状态：待回答
        .build();
questionMapper.insert(question);
```

**Question 实体**（表名：`question`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `user_id` | UUID | 提问用户 |
| `question` | String | 问题文本 |
| `knowledge_base_id` | UUID | 限定知识库（可为 null） |
| `status` | enum | PENDING → ANSWERED / FAILED |
| `created_at` | LocalDateTime | MyBatis-Plus 自动填充 |

**状态机**：
```
PENDING ──→ ANSWERED (成功)
        └─→ FAILED   (异常)
```

---

### 3️⃣ 问题向量化（AgentService → VectorService.embed）

**代码**：[`AgentService.java:38-39`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/AgentService.java#L38-L39)

```java
float[] questionEmbedding = vectorService.embed(question);
```

`VectorService.embed()` 的实现（[`VectorService.java:77-95`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/VectorService.java#L77-L95)）：

```java
public float[] embed(String text) {
    try {
        // 主策略：调用阿里云 DashScope text-embedding-v4 API
        float[] result = embeddingModel.embed(text);  // ← Spring AI EmbeddingModel
        return result;
    } catch (Exception e) {
        // 降级策略：API 不可用时使用 hash 伪随机向量
        return generateFallbackEmbedding(text);
    }
}
```

**关键技术点**：

- `EmbeddingModel` 是 Spring AI 提供的**统一嵌入模型接口**，无论是阿里云 DashScope、OpenAI 还是本地模型，都通过同一个接口调用
- `embeddingModel.embed(text)` 返回 `float[1536]` —— **1536 维向量**
- 配置了降级策略：API 不可用时不会直接报错，而是生成 hash 伪随机向量保证系统可用

配置在 `application.yml`：
```yaml
dashscope:
  api-key: sk-xxxx
  embedding:
    model: text-embedding-v4
    dimensions: 1536
```

同时关闭了 Spring AI OpenAI 自带的 embedding（因为用 DashScope 替代）：
```yaml
spring.ai.openai.embedding.enabled: false
```

---

### 4️⃣ 向量相似度检索（VectorService → pgvector）

**代码**：[`AgentService.java:41-42`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/AgentService.java#L41-L42)

```java
List<DocumentChunk> similarChunks = vectorService.searchByCosineSimilarity(
        questionEmbedding, knowledgeBaseId, 5);
```

`VectorService.searchByCosineSimilarity()`（[`VectorService.java:41-44`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/VectorService.java#L41-L44)）：

```java
public List<DocumentChunk> searchByCosineSimilarity(
        float[] queryEmbedding, UUID knowledgeBaseId, int limit) {
    String vectorString = arrayToString(queryEmbedding);  // float[] → "[0.1, 0.2, ...]"
    return documentChunkVectorMapper.findByCosineSimilarity(
            vectorString, null, knowledgeBaseId, limit);
}
```

**执行的 SQL**（[`DocumentChunkVectorMapper.xml`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/resources/mapper/DocumentChunkVectorMapper.xml#L41-L57)）：

```sql
SELECT dc.id, dc.document_id, dc.chunk_index, dc.content,
       1 - (dv.embedding <=> CAST(#{queryVector} AS vector)) AS similarity
FROM document_chunk dc
LEFT JOIN document_chunk_vector dv ON dc.id = dv.id
LEFT JOIN documents d ON dc.document_id = d.id
<where>
    <if test="knowledgeBaseId != null">
        d.knowledge_base_id = #{knowledgeBaseId}  -- 限定知识库
    </if>
</where>
ORDER BY similarity DESC
LIMIT 5
```

**关键点**：
- `<=>` 是 pgvector 的**余弦距离**运算符
- `1 - 余弦距离` = **余弦相似度**，值越大越相关
- `knowledgeBaseId` 为 null 时不加过滤条件 → 跨所有知识库检索
- `LIMIT 5` → 取最相似的 5 个文档片段

**pgvector 支持的三种检索方式**：

| 方法 | 运算符 | 说明 |
|------|--------|------|
| `findByCosineSimilarity` | `<=>` | 余弦相似度（本项目默认） |
| `findNearestL2` | `<#>` | L2 欧氏距离 |
| `findByNegativeInnerProduct` | `<@>` | 负内积（等价于内积排序） |

---

### 5️⃣ 构建 Prompt（AgentService - SystemMessage + UserMessage）

**代码**：[`AgentService.java:44-51`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/AgentService.java#L44-L51)

```java
// 将检索到的 chunk 拼接为上下文
String context = buildContext(similarChunks);

// 构建引用来源列表
List<AnswerResponse.SourceInfo> sources = buildSources(similarChunks);

// 使用 Spring AI 构建 Prompt
Prompt prompt = new Prompt(List.of(
        new SystemMessage(SYSTEM_PROMPT),
        new UserMessage(
            "【检索到的相关文档内容】\n\n" + context +
            "\n【用户问题】\n" + question +
            "\n\n请基于以上文档内容回答用户的问题。")
));
```

**System Prompt（系统提示词）**：

```
你是一个企业知识库智能助手。请根据以下检索到的文档内容回答用户的问题。

要求：
1. 只使用提供的文档内容回答，不要编造信息
2. 如果文档内容不足以回答用户的问题，请明确告知
3. 回答要简洁、准确、有条理
4. 引用具体文档内容时请说明来源
5. 使用中文回答
```

**最终发送给 LLM 的完整 Prompt 结构**：

```
[SystemMessage]: 你是一个企业知识库智能助手...

[UserMessage]:
【检索到的相关文档内容】

文档片段1：
<chunk 1 的文本内容>

文档片段2：
<chunk 2 的文本内容>

...

【用户问题】
请假流程是什么？

请基于以上文档内容回答用户的问题。
```

---

### 6️⃣ 调用 DeepSeek 大模型（ChatModel.call）

**代码**：[`AgentService.java:53-64`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/AgentService.java#L53-L64)

```java
try {
    // Spring AI ChatModel.call() → 自动路由到 deepseek-chat
    String answer = chatModel.call(prompt)
            .getResult()           // ChatResponse
            .getOutput()           // AssistantMessage
            .getText();            // String

    return AnswerResponse.builder()
            .answer(answer)
            .sources(sources)
            .confidence(calculateConfidence(similarChunks))
            .build();
} catch (Exception e) {
    // LLM 调用失败时的降级策略
    return fallbackAnswer(question, sources);
}
```

**调用链路**：

```
chatModel.call(prompt)
  └─ Spring AI OpenAI ChatModel
      └─ HTTP POST https://api.deepseek.com/v1/chat/completions
          Headers: Authorization: Bearer sk-xxx
          Body: {
                  "model": "deepseek-chat",
                  "messages": [
                    {"role": "system", "content": "你是一个企业知识库..."},
                    {"role": "user", "content": "【检索到的相关文档内容】..."}
                  ],
                  "temperature": 0.7
                }
```

**Spring AI 在这里的作用**：`ChatModel` 是一个接口，运行时注入的是 OpenAI 兼容实现。通过 `spring.ai.openai.base-url: https://api.deepseek.com` 将请求路由到 DeepSeek 而非 OpenAI。

**降级策略（LLM 调用失败时）**：

```java
private AnswerResponse fallbackAnswer(String question, List<SourceInfo> sources) {
    // 不依赖 LLM，直接从检索结果拼接回答
    String answer = "根据知识库中的文档内容，以下是相关信息：\n\n";
    if (!sources.isEmpty()) {
        answer += sources.stream()
                .map(s -> "📄 " + s.getChunkContent())
                .collect(Collectors.joining("\n\n"));
    } else {
        answer += "未找到与您问题直接相关的文档内容...";
    }
    return AnswerResponse.builder()
            .answer(answer).sources(sources).confidence(0.5f).build();
}
```

---

### 7️⃣ 答案持久化（QAService - 步骤 2-5）

**代码**：[`QAService.java:143-174`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/QAService.java#L143-L174)

```java
// 步骤 3：创建 answer 实体
Answer answerEntity = Answer.builder()
        .id(UUID.randomUUID())
        .questionId(question.getId())        // 1:1 关联 question
        .answer(answer.getAnswer())          // AI 生成的答案
        .confidence(answer.getConfidence())  // 置信度
        .build();

// 步骤 4：序列化引用来源为 JSON
answerEntity.setSources(objectMapper.writeValueAsString(answer.getSources()));

answerMapper.insert(answerEntity);

// 步骤 5：更新 question 状态
question.setStatus(Question.Status.ANSWERED);
questionMapper.updateById(question);
```

**Answer 实体**（表名：`answer`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `question_id` | UUID | 关联 question 表（1:1） |
| `answer` | String | AI 生成的答案正文 |
| `sources` | TEXT(JSON) | 引用来源 JSON 数组 |
| `confidence` | Float | 整体置信度（0.0~1.0） |
| `created_at` | LocalDateTime | 自动填充 |

**sources JSON 示例**：
```json
[
  {
    "documentId": "a0967b38-da97-4797-879f-06e95e13d1dc",
    "documentTitle": "知识库文档",
    "chunkContent": "请假流程：1. 填写请假申请单...",
    "confidence": 0.85
  }
]
```

---

### 8️⃣ 返回响应

**`AnswerResponse` DTO**：

```java
public class AnswerResponse {
    private String answer;           // AI 生成的答案文本
    private List<SourceInfo> sources; // 引用来源列表
    private Float confidence;        // 整体置信度

    public static class SourceInfo {
        private UUID documentId;      // 来源文档 ID
        private String documentTitle; // 来源文档标题
        private String chunkContent;  // 片段内容（截断到 200 字符）
        private Float confidence;     // 来源置信度
    }
}
```

**HTTP 响应示例**：
```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "answer": "根据知识库内容，请假流程如下：\n1. 员工...",
    "sources": [
      {
        "documentId": "a0967b38-...",
        "documentTitle": "知识库文档",
        "chunkContent": "请假流程：1. 填写请假申请单...",
        "confidence": 0.85
      }
    ],
    "confidence": 0.9
  }
}
```

---

### 9️⃣ 对话历史查询

**接口**：`GET /api/qa/history?page=0&pageSize=10`

**代码**：[`QAService.java:208-224`](file:///Users/sunguangxu/Documents/trae_projects/langchaindemo/rag-search-service/src/main/java/com/example/rag/service/QAService.java#L208-L224)

```java
public PagedResponse<Question> getQuestionHistory(UUID userId, int page, int pageSize) {
    Page<Question> pageParam = new Page<>(page + 1, pageSize);  // MyBatis-Plus 从 1 开始
    IPage<Question> questionPage = questionMapper.findByUserId(pageParam, userId);
    return PagedResponse.<Question>builder()
            .list(questionPage.getRecords())
            .total(questionPage.getTotal())
            .page(page)          // 返回 0-based 页码给前端
            .pageSize(pageSize)
            .totalPages((int) questionPage.getPages())
            .build();
}
```

---

## Spring AI 框架详解

### 3.1 什么是 Spring AI

Spring AI 是 Spring 生态下的 AI 应用开发框架（版本 1.0.0），核心设计理念是 **AI 模型的可移植性** —— 用统一的 API 接口屏蔽不同 AI 服务商的差异，让切换模型就像切换数据库驱动一样简单。

```
              ┌───────────────────────┐
              │   Spring AI API       │
              │  (ChatModel,          │
              │   EmbeddingModel,     │
              │   Prompt, Message)     │
              └───────────┬───────────┘
                          │
          ┌───────────────┼───────────────┐
          │               │               │
    ┌─────┴─────┐  ┌─────┴─────┐  ┌─────┴─────┐
    │ OpenAI    │  │ Azure     │  │ Ollama    │  ... 更多提供商
    │ (DeepSeek)│  │ OpenAI    │  │ (本地)     │
    └───────────┘  └───────────┘  └───────────┘
```

### 3.2 本项目的依赖配置

Maven 依赖（`pom.xml`）：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

这个 starter 自动引入：
- `spring-ai-openai` —— OpenAI API 客户端实现
- `spring-ai-openai-spring-boot-autoconfigure` —— 自动配置
- `spring-ai-core` —— 核心 API（`ChatModel`、`EmbeddingModel`、`Prompt` 等）

### 3.3 ChatModel —— 聊天模型

**接口定义**（Spring AI 核心 API）：

```java
public interface ChatModel extends Model<Prompt, ChatResponse> {
    ChatResponse call(Prompt prompt);
    // + 流式方法 call(StreamingPrompt) 等
}
```

**本项目中的注入**：

```java
@Service
public class AgentService {
    private final ChatModel chatModel;   // ← Spring 自动注入

    public AnswerResponse answer(String question, UUID knowledgeBaseId) {
        // ...
        String answer = chatModel.call(prompt)
                .getResult()      // ChatResponse → Generation
                .getOutput()      // Generation → AssistantMessage
                .getText();       // AssistantMessage → String
        // ...
    }
}
```

**调用结果链**：

```
chatModel.call(prompt)
  └─ ChatResponse
      └─ .getResult() → Generation
          └─ .getOutput() → AssistantMessage (继承 AbstractMessage)
              ├─ .getText() → String（答案正文）
              └─ .getMetadata() → Map<String, Object>（token 用量等）
```

**为什么用 DeepSeek 而不是 OpenAI**：
- DeepSeek 提供了**与 OpenAI 完全兼容的 API 格式**
- 只需改 `spring.ai.openai.base-url` 即可无缝切换
- DeepSeek `deepseek-chat` 中文能力更强、成本更低

### 3.4 EmbeddingModel —— 嵌入模型

**接口定义**（Spring AI 核心 API）：

```java
public interface EmbeddingModel extends Model<EmbeddingRequest, EmbeddingResponse> {
    EmbeddingResponse call(EmbeddingRequest request);

    // 便捷方法
    float[] embed(String text);          // 单文本嵌入
    List<float[]> embed(List<String> texts); // 批量嵌入
    default int dimensions();            // 向量维度
}
```

**本项目中的使用**：

```java
@Service
public class VectorService {
    private final EmbeddingModel embeddingModel;  // ← Spring AI 注入

    public float[] embed(String text) {
        return embeddingModel.embed(text);  // 返回 float[1536]
    }
}
```

**注意**：本项目中 `EmbeddingModel` 被绑定到阿里云 DashScope 而非 OpenAI。因为 `application.yml` 中配置了：

```yaml
spring.ai.openai.embedding.enabled: false    # 关闭 OpenAI Embedding

dashscope:                                   # 使用阿里云 DashScope
  api-key: sk-xxx
  embedding:
    model: text-embedding-v4
    dimensions: 1536
```

### 3.5 Prompt / Message —— 提示词体系

Spring AI 提供了结构化的提示词对象模型：

```
Prompt（提示词）
  └─ List<Message>（消息列表）
      ├─ SystemMessage（系统消息）   → role: "system"
      ├─ UserMessage（用户消息）     → role: "user"
      ├─ AssistantMessage（助手消息） → role: "assistant"
      └─ FunctionMessage（函数消息） → role: "function"
```

**本项目中的使用**：

```java
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

Prompt prompt = new Prompt(List.of(
    new SystemMessage("你是一个企业知识库智能助手..."),
    new UserMessage("检索内容 + 用户问题...")
));

ChatResponse response = chatModel.call(prompt);
```

**发送到 DeepSeek API 的实际 JSON**：

```json
{
  "model": "deepseek-chat",
  "messages": [
    {"role": "system", "content": "你是一个企业知识库智能助手..."},
    {"role": "user", "content": "【检索到的相关文档内容】\n\n..."}
  ],
  "temperature": 0.7
}
```

### 3.6 application.yml 配置详解

```yaml
spring:
  ai:
    openai:
      api-key: sk-1fe6875284c74e60ac6d7f2062773061   # DeepSeek API Key
      base-url: https://api.deepseek.com              # ← 关键：指向 DeepSeek
      chat:
        options:
          model: deepseek-chat                         # 模型名
          temperature: 0.7                             # 创造性（0=确定，1=随机）
      embedding:
        enabled: false                                 # ← 不用 OpenAI embedding

dashscope:                                            # 阿里云 DashScope（非 Spring AI 原生）
  api-key: sk-cbb9eff1415f46b4b954c9bb9129e341
  embedding:
    model: text-embedding-v4
    dimensions: 1536
```

**配置说明**：
- `spring.ai.openai.base-url` 指向 DeepSeek → ChatModel 自动路由到 DeepSeek
- `spring.ai.openai.chat.options.model` 指定使用 `deepseek-chat` 模型
- `temperature: 0.7` 控制回答的创造性，知识库答疑建议 0.3~0.7
- `embedding.enabled: false` 关闭 OpenAI embedding，改用 DashScope

### 3.7 Spring AI 可移植性原理

假设要从 DeepSeek 切换到 Azure OpenAI，只需改配置：

```yaml
# 从 DeepSeek
spring.ai.openai.base-url: https://api.deepseek.com
spring.ai.openai.chat.options.model: deepseek-chat

# 改为 Azure OpenAI
spring.ai.azure.openai.api-key: ${AZURE_OPENAI_KEY}
spring.ai.azure.openai.endpoint: https://xxx.openai.azure.com
spring.ai.azure.openai.chat.options.deployment-name: gpt-4
```

**代码**（`AgentService`）**不需要任何修改！** `ChatModel` 接口隔离了具体实现。

同样，Embedding 模型也可以切换：

```yaml
# 从 DashScope（当前）
# ...自定义配置

# 改为 OpenAI embedding
spring.ai.openai.embedding.enabled: true
spring.ai.openai.embedding.options.model: text-embedding-ada-002
```

### 3.8 常见问题与最佳实践

#### Q1: Spring AI ChatModel 和直接调用 HTTP API 有什么区别？

| | Spring AI ChatModel | 直接 HTTP 调用 |
|---|---|---|
| 代码量 | `chatModel.call(prompt)` 一行 | 需手动构建请求、解析响应 |
| 可移植性 | 换模型改配置即可 | 需要改代码 |
| 错误处理 | 框架统一处理 | 需自己实现 |
| 流式输出 | `Flux<ChatResponse> stream()` | 需处理 SSE 流 |
| 类型安全 | 强类型 Prompt/Message | JSON 字符串拼接 |

#### Q2: 为什么 embedding 不用 Spring AI，而是自定义 DashScope？

Spring AI 1.0 的 `EmbeddingModel` 对 DashScope 的支持是通过 OpenAI 兼容适配的，本项目通过自定义配置绑定到阿里云原生 DashScope SDK，获取更好的性能和中文语义效果。

#### Q3: 如何实现流式输出（SSE）？

```java
// 非流式（当前）
String answer = chatModel.call(prompt).getResult().getOutput().getText();

// 流式（Spring AI 原生支持）
Flux<ChatResponse> stream = chatModel.stream(prompt);
return stream.map(r -> r.getResult().getOutput().getText());
// 前端通过 Server-Sent Events 逐步接收
```

#### Q4: Prompt 中如何保留多轮对话历史？

```java
List<Message> messages = new ArrayList<>();
messages.add(new SystemMessage("你是一个企业知识库助手"));

// 添加历史对话
for (Conversation conv : history) {
    messages.add(new UserMessage(conv.getQuestion()));
    messages.add(new AssistantMessage(conv.getAnswer()));
}

// 添加当前问题
messages.add(new UserMessage("当前的检索上下文 + 用户问题"));

Prompt prompt = new Prompt(messages);
```

---

## 核心代码文件清单

| 文件 | 路径 | 职责 |
|------|------|------|
| `QAController` | `controller/QAController.java` | HTTP 接口，JWT 鉴权，`@Valid` 校验 |
| `QAService` | `service/QAService.java` | 问答编排：持久化问题→调用 Agent→持久化答案 |
| `AgentService` | `service/AgentService.java` | RAG 核心：向量化→检索→Prompt→LLM |
| `VectorService` | `service/VectorService.java` | 向量生成 + pgvector 检索 |
| `QuestionRequest` | `dto/request/QuestionRequest.java` | 请求 DTO（question + knowledgeBaseId） |
| `AnswerResponse` | `dto/response/AnswerResponse.java` | 响应 DTO（answer + sources + confidence） |
| `Question` | `entity/Question.java` | question 表实体 |
| `Answer` | `entity/Answer.java` | answer 表实体 |
| `QuestionMapper` | `mapper/QuestionMapper.java` | question 表 DAO |
| `AnswerMapper` | `mapper/AnswerMapper.java` | answer 表 DAO |
| `DocumentChunkVectorMapper` | `mapper/DocumentChunkVectorMapper.java` | 向量检索 Mapper 接口 |
| `DocumentChunkVectorMapper.xml` | `resources/mapper/DocumentChunkVectorMapper.xml` | 向量检索 SQL |
| `application.yml` | `resources/application.yml` | Spring AI / DeepSeek / DashScope 配置 |
| `pom.xml` | `pom.xml` | `spring-ai-starter-model-openai` 依赖 |

---

## 数据模型

### Question（问题实体）

```java
@TableName("question")
public class Question {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;
    private UUID userId;           // 提问用户
    private String question;       // 问题文本
    private UUID knowledgeBaseId;  // 限定知识库（可为 null）
    private Status status;         // PENDING / ANSWERED / FAILED
    private LocalDateTime createdAt;

    public enum Status { PENDING, ANSWERED, FAILED }
}
```

### Answer（答案实体）

```java
@TableName("answer")
public class Answer {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;
    private UUID questionId;       // 关联 question 表（1:1）
    private String answer;         // AI 生成的答案
    private String sources;        // JSON 格式的引用来源
    private Float confidence;      // 置信度
    private LocalDateTime createdAt;
}
```

---

## 时序图

```
Client      QAController      QAService      AgentService     VectorService     pgvector      DeepSeek API
  │              │                 │                │                │                │              │
  │ POST /question│                │                │                │                │              │
  │ ────────────>│                 │                │                │                │              │
  │              │ JWT 鉴权        │                │                │                │              │
  │              │ getUserId()     │                │                │                │              │
  │              │                 │                │                │                │              │
  │              │ askQuestion()   │                │                │                │              │
  │              │ ───────────────>│                │                │                │              │
  │              │                 │ INSERT question │                │                │              │
  │              │                 │ (PENDING)       │                │                │              │
  │              │                 │ ───────────────────────────────────────────────>│              │
  │              │                 │                │                │                │              │
  │              │                 │ answer(q, kbId) │                │                │              │
  │              │                 │ ───────────────>│                │                │              │
  │              │                 │                │                │                │              │
  │              │                 │                │ embed(question)│                │              │
  │              │                 │                │ ──────────────>│                │              │
  │              │                 │                │                │ DashScope API   │              │
  │              │                 │                │ <──────────────│ float[1536]     │              │
  │              │                 │                │                │                │              │
  │              │                 │                │ searchByCosine │                │              │
  │              │                 │                │ ──────────────>│                │              │
  │              │                 │                │                │ <=> 相似度查询  │              │
  │              │                 │                │ <──────────────│ top-5 chunks    │              │
  │              │                 │                │                │                │              │
  │              │                 │                │ buildContext(chunks)             │              │
  │              │                 │                │ buildSources(chunks)             │              │
  │              │                 │                │                │                │              │
  │              │                 │                │ new Prompt(                      │              │
  │              │                 │                │   SystemMessage,                 │              │
  │              │                 │                │   UserMessage)                   │              │
  │              │                 │                │                │                │              │
  │              │                 │                │ chatModel.call(prompt)           │              │
  │              │                 │                │ ────────────────────────────────>│ deepseek-chat│
  │              │                 │                │ <────────────────────────────────│ answer text  │
  │              │                 │                │                │                │              │
  │              │                 │ <──────────────│ AnswerResponse  │                │              │
  │              │                 │                │                │                │              │
  │              │                 │ INSERT answer  │                │                │              │
  │              │                 │ (sources JSON)  │                │                │              │
  │              │                 │ ───────────────────────────────────────────────>│              │
  │              │                 │                │                │                │              │
  │              │                 │ UPDATE question │                │                │              │
  │              │                 │ (ANSWERED)      │                │                │              │
  │              │                 │ ───────────────────────────────────────────────>│              │
  │              │                 │                │                │                │              │
  │              │ <───────────────│ AnswerResponse  │                │                │              │
  │              │                 │                │                │                │              │
  │ <────────────│ 200 + data      │                │                │                │              │
```

---

## 快速测试

### 提问

```bash
# 1. 获取 Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser2024","password":"123456"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")

# 2. 全局检索提问（不限定知识库）
curl -s -X POST http://localhost:8080/api/qa/question \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"请假流程是什么？"}' | python3 -m json.tool

# 3. 限定知识库提问
curl -s -X POST http://localhost:8080/api/qa/question \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"请假流程是什么？","knowledgeBaseId":"cea53e8d-3fda-4797-9fdb-fe03c5a5bc19"}' \
  | python3 -m json.tool
```

### 查询对话历史

```bash
curl -s "http://localhost:8080/api/qa/history?page=0&pageSize=5" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

### 验证向量检索效果

```sql
-- 查看某次问答中使用的检索结果
SELECT dc.chunk_index, dc.content,
       1 - (dv.embedding <=> (
           SELECT dv2.embedding FROM document_chunk_vector dv2
           WHERE dv2.id = '某个 chunk_id'
       )) AS similarity
FROM document_chunk dc
JOIN document_chunk_vector dv ON dc.id = dv.id
ORDER BY similarity DESC
LIMIT 5;
```
