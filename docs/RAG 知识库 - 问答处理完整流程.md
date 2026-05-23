# RAG 知识库 - 问答处理完整流程

> Spring AI 1.1.6 + Tool Calling + MCP 协议 —— 重构版本

本文档基于 `rag-search-service` 实际代码重构后版本，详细描述 `/api/qa/question` 问答接口从 HTTP 请求到 LLM 生成答案的完整处理链路。

---

## 📚 目录

1. [问答接口总体架构](#问答接口总体架构)
2. [步骤详解](#步骤详解)
   - [① 请求入口（QAController）](#1️⃣-请求入口qacontroller)
   - [② 问题持久化（QAService）](#2️⃣-问题持久化qaservice)
   - [③ 问题向量化（VectorService）](#3️⃣-问题向量化vectorservice)
   - [④ 向量相似度检索（VectorService → pgvector）](#4️⃣-向量相似度检索vectorservice--pgvector)
   - [⑤ 构建消息列表（AgentService.buildMessages）](#5️⃣-构建消息列表agentservicebuildmessages)
   - [⑥ ChatClient.tools()——LLM 自主决策工具调用](#6️⃣-chatclienttoolsllm-自主决策工具调用)
   - [⑦ 答案持久化（QAService）](#7️⃣-答案持久化qaservice)
   - [⑧ 返回响应](#8️⃣-返回响应)
   - [⑨ 对话历史与多轮上下文](#9️⃣-对话历史与多轮上下文)
   - [⑩ 会话管理](#0️⃣-会话管理)
3. [Tool Calling 详解](#tool-calling-详解)
   - [3.1 @Tool 注解机制](#31-tool-注解机制)
   - [3.2 JSON Schema 自动生成](#32-json-schema-自动生成)
   - [3.3 LLM 函数调用多轮协作](#33-llm-函数调用多轮协作)
   - [3.4 工具类清单](#34-工具类清单)
4. [MCP 协议集成](#mcp-协议集成)
   - [4.1 MCP 三层架构](#41-mcp-三层架构)
   - [4.2 @McpTool 注解](#42-mcptool-注解)
   - [4.3 协议交互时序](#43-协议交互时序)
   - [4.4 依赖与配置](#44-依赖与配置)
5. [Spring AI 框架详解](#spring-ai-框架详解)
6. [容错与降级策略](#容错与降级策略)
7. [核心代码文件清单](#核心代码文件清单)
8. [数据模型](#数据模型)
9. [时序图（完整）](#时序图完整)
10. [快速测试](#快速测试)

---

## 问答接口总体架构

### 请求入口

```
POST /api/qa/question
{
  "question": "秦PLUS落地多少钱？",
  "knowledgeBaseId": "cea53e8d-...",   // 可选
  "sessionId": "f47ac10b-..."          // 可选，不传则新建会话
}
```

### 全链路处理流程

```
┌──────────────────────────────────────────────────────────────────────┐
│                        QAController                                   │
│  • JWT 鉴权 → getCurrentUserId()                                     │
│  • 参数校验 @Valid                                                     │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                         QAService                                     │
│  @Transactional                                                       │
│                                                                       │
│  ① INSERT question (status=PENDING, session_id=xxx)                   │
│  ② agentService.answer(question, knowledgeBaseId, sessionId)          │
│  ③ INSERT answer (answer + sources JSON + confidence)                 │
│  ④ UPDATE question (status=ANSWERED)                                  │
│  ⑤ return AnswerResponse                                              │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ ②
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                      AgentService (RAG + Tool Calling 核心)            │
│                                                                       │
│  1. vectorService.embed(question)                                     │
│     └─ 阿里云 DashScope text-embedding-v4 → float[1536]               │
│                                                                       │
│  2. vectorService.searchByCosineSimilarity(embedding, kbId, 5)        │
│     └─ pgvector <=> 余弦相似度 → top-5 DocumentChunk                  │
│                                                                       │
│  3. buildMessages() → SystemMessage + 多轮历史 + RAG上下文 + 问题     │
│                                                                       │
│  4. ChatClient.builder(chatModel)                                     │
│          .prompt()                                                    │
│          .messages(messages)                                          │
│          .tools(carPriceCalculator, automotiveTools)  ← ⚡ 注入工具     │
│          .call()                                                      │
│          .content()                                                   │
│     └─ ┌─────────────────────────────────────────────────────────┐   │
│        │ LLM 自主决策：                                            │   │
│        │   • 用户问落地价？→ 返回 function_call(carPriceCalculator)│   │
│        │   • 用户问优惠？ → 返回 function_call(automotiveTools)    │   │
│        │   • 知识问答？   → 直接基于 RAG 上下文回答                 │   │
│        │ ChatClient 自动执行工具 → 结果回传 LLM → 生成最终答案       │   │
│        └─────────────────────────────────────────────────────────┘   │
│                                                                       │
│  5. return AnswerResponse(answer, sources, confidence)                 │
└──────────────────────────────────────────────────────────────────────┘
```

### 三层能力矩阵

| 层次 | 服务类 | 核心技术 | 新旧对比 |
|------|--------|---------|---------|
| ① 入口 | `QAController` | JWT 鉴权、`@Valid` 校验 | 不变 |
| ② 编排 | `QAService` | `@Transactional`、问题→答案双表写入 | 不变 |
| ③ 向量化 | `VectorService.embed()` | 阿里云 DashScope `text-embedding-v4`（1536 维） | 不变 |
| ④ 检索 | `VectorService.searchByCosineSimilarity()` | pgvector `<=>` 余弦相似度 | 不变 |
| ⑤ 消息构建 | `AgentService.buildMessages()` | System + History + RAG 上下文 | **新增多轮历史支持** |
| ⑥ 工具决策 | `AgentService.callChatClientWithTools()` | `ChatClient.tools()` + `@Tool` 注解 | **🎉 替代手动正则匹配** |
| ⑦ MCP 暴露 | `McpToolsAdapter` | `@McpTool` + Streamable-HTTP | **🎉 新增对外标准接口** |
| ⑧ LLM 调用 | `ChatClient` → DeepSeek | `deepseek-chat`（OpenAI 兼容 API） | 扁平 API 升级为 Fluent API |
| ⑨ 容错 | `@Retryable` + `@Recover` | 指数退避重试 + 业务降级 | **新增三层防护** |

---

## 步骤详解

### 1️⃣ 请求入口（QAController）

**接口**：`POST /api/qa/question`

**请求体**（`QuestionRequest`）：

```json
{
  "question": "秦PLUS落地多少钱？",
  "knowledgeBaseId": "cea53e8d-3fda-4797-9fdb-fe03c5a5bc19",
  "sessionId": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | String | ✅ | 用户输入的自然语言问题，`@NotBlank` 校验 |
| `knowledgeBaseId` | UUID | ❌ | 限定检索的知识库 ID，传 `null` 则全局检索所有知识库 |
| `sessionId` | UUID | ❌ | 多轮对话会话 ID，不传则自动新建 |

**权限**：`@PreAuthorize("isAuthenticated()")` —— 任意已登录用户

```java
@PostMapping("/question")
public ResponseEntity<ApiResponse<AnswerResponse>> askQuestion(
        @Valid @RequestBody QuestionRequest request) {
    UUID userId = getCurrentUserId();        // 从 JWT Token 提取当前用户 ID
    AnswerResponse response = qaService.askQuestion(request, userId);
    return ResponseEntity.ok(success(response));
}
```

**`getCurrentUserId()` 实现**（`BaseController`）：

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

### 2️⃣ 问题持久化（QAService）

```java
@Transactional
public AnswerResponse askQuestion(QuestionRequest request, UUID userId) {
    // 使用请求中的会话ID，或生成新的会话ID
    UUID sessionId = request.getSessionId();
    if (sessionId == null) {
        sessionId = UUID.randomUUID();
    }

    // 步骤1：构建并保存问题实体
    Question question = Question.builder()
            .id(UUID.randomUUID())                         // MyBatis-Plus ASSIGN_UUID
            .userId(userId)
            .sessionId(sessionId)                          // 多轮会话关联
            .question(request.getQuestion())               // 用户原始问题文本
            .knowledgeBaseId(request.getKnowledgeBaseId()) // 可选，限定知识库
            .status(Question.Status.PENDING)               // 初始状态：待回答
            .build();
    questionMapper.insert(question);

    // 步骤2：调用 AgentService（核心 RAG + Tool Calling 逻辑）
    AnswerResponse answer = agentService.answer(
            request.getQuestion(), request.getKnowledgeBaseId(), request.getSessionId());
    // ...
}
```

**Question 实体**（表名：`question`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `user_id` | UUID | 提问用户 |
| `session_id` | UUID | 多轮对话会话 ID |
| `question` | String | 问题文本 |
| `knowledge_base_id` | UUID | 限定知识库（可为 null） |
| `status` | enum | PENDING → ANSWERED / FAILED |
| `created_at` | LocalDateTime | MyBatis-Plus 自动填充 |

---

### 3️⃣ 问题向量化（VectorService）

```java
float[] questionEmbedding = vectorService.embed(question);
```

`VectorService.embed()` 实现：

```java
public float[] embed(String text) {
    try {
        // 主策略：调用阿里云 DashScope text-embedding-v4 API
        return embeddingModel.embed(text);  // ← Spring AI EmbeddingModel 统一接口
    } catch (Exception e) {
        // 降级策略：API 不可用时使用 hash 伪随机向量保证系统可用
        return generateFallbackEmbedding(text);
    }
}
```

**关键技术点**：

| 维度 | 说明 |
|------|------|
| 接口统一 | `EmbeddingModel` 是 Spring AI 的嵌入模型抽象，切换供应商无需改代码 |
| 向量维度 | `text-embedding-v4` 输出 **1536 维** 浮点向量 |
| 降级策略 | API 不可用时使用 hash 伪随机向量，保证系统不崩溃 |

---

### 4️⃣ 向量相似度检索（VectorService → pgvector）

```java
List<DocumentChunk> similarChunks = vectorService.searchByCosineSimilarity(
        questionEmbedding, knowledgeBaseId, 5);   // top-5
```

**执行的 SQL**（`DocumentChunkVectorMapper.xml`）：

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
LIMIT #{limit}
```

| 运算符 | 含义 | 转化 |
|--------|------|------|
| `<=>` | pgvector 余弦距离 | `1 - 余弦距离` = 余弦相似度（越大越相关） |
| `<#>` | L2 欧氏距离 | 距离越小越相似 |
| `<@>` | 负内积 | 等价于内积排序 |

---

### 5️⃣ 构建消息列表（AgentService.buildMessages）

这是 RAG 上下文与 Tool Calling 的**衔接层**，消息结构决定 LLM 的决策质量。

```java
private List<Message> buildMessages(String question, String ragContext,
                                     UUID knowledgeBaseId, UUID sessionId) {
    List<Message> messages = new ArrayList<>();

    // 1️⃣ 系统提示词：定义角色 + 能力边界 + 工具使用引导
    messages.add(new SystemMessage(selectSystemPrompt(knowledgeBaseId)));

    // 2️⃣ 多轮对话历史：从数据库加载，构建 User-Assistant 交替序列
    if (sessionId != null) {
        List<Question> history = questionMapper.findBySessionIdOrderByCreatedAt(sessionId);
        for (Question q : history) {
            Answer a = answerMapper.findByQuestionId(q.getId()).orElse(null);
            messages.add(new UserMessage(q.getQuestion()));
            if (a != null && a.getAnswer() != null) {
                messages.add(new AssistantMessage(a.getAnswer()));
            }
        }
    }

    // 3️⃣ RAG 上下文 + 用户问题：检索结果作为 user message 的一部分注入
    messages.add(new UserMessage(
            "【检索到的相关文档内容】\n\n" + ragContext +
            "\n【用户问题】\n" + question +
            "\n\n请基于以上文档内容回答用户的问题。"));

    return messages;
}
```

**系统提示词（汽车销售场景）**：

```
你是一个专业的汽车销售顾问AI助手，正在辅助直播间销售场景。

## 可用工具
你可以调用可用的工具函数来获取实时数据（如价格计算、月供计算、优惠活动、联系方式等）。
当用户询问价格、月供、优惠、活动、联系方式等问题时，优先调用对应工具获取准确数据。

## 回答要求：
1. 只使用提供的文档内容回答，绝不编造不存在的信息
2. 语气亲切热情，像真人销售顾问一样
3. 价格务必精确，不要模糊；有多个配置时要分别说明
4. 使用中文回答
```

---

### 6️⃣ ChatClient.tools()——LLM 自主决策工具调用

这是本次重构的**核心变更**，用 LLM 的语义理解能力替代了旧版的手动正则匹配。

**代码**：

```java
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
public String callChatClientWithTools(List<Message> messages) {
    return ChatClient.builder(chatModel).build()
            .prompt()                              // 2. 进入 prompt 构建模式
            .messages(messages)                    // 3. 设置对话消息
            .tools(carPriceCalculator, automotiveTools) // 4. ⚡ 注入工具实例
            .call()                                // 5. 发起调用
            .content();                            // 6. 获取最终文本
}
```

**执行流程对比**：

```
旧方案（手动正则调度）：                         新方案（LLM 自主决策）：

用户输入："秦PLUS落地多少钱"                      用户输入："秦PLUS落地多少钱"
  │                                                │
  ├─ 正则1: ".*(落地多少钱|落地价).*" 命中           │
  │   └─ 正则2: ".*(秦|汉|唐).*" 命中                │  ChatClient
  │       └─ extractCarModel() → "秦PLUS"            │    .messages("你是销售顾问...")
  │           └─ calculateOnRoadPrice("秦PLUS")       │    .tools(carPriceCalculator,
  │                                                    │            automotiveTools)
  │                                                    │
  问题："这个车全办完多钱"                            │    ┌─ LLM 理解语义 ─┐
  │   ✗ 正则1 不匹配（没有"落地"关键词）              │    │ "用户想算落地价"  │
  │   ✗ 正则2 ...                                    │    │ carModel=秦PLUS   │
  └─ 走 RAG 流程（可能回答不准确）                     │    └─────┬───────────┘
                                                      │          │
                                                      │ 返回 function_call:
                                                      │ { "name": "calculateOnRoadPrice",
                                                      │   "arguments": {"carModel":"秦PLUS"} }
                                                      │          │
                                                      │ ChatClient 自动执行工具
                                                      │ → 结果回传 LLM
                                                      │ → 生成最终回答
```

**五种 LLM 自主决策场景**：

| 用户输入 | LLM 决策 | 调用的工具 |
|---------|---------|-----------|
| "秦PLUS落地多少钱" | 需要实时价格数据 | `calculateOnRoadPrice("秦PLUS")` |
| "汉兰达首付3成分5年月供多少" | 需要分期计算 | `calculateMonthlyPayment("汉兰达", 0.3, 5)` |
| "你们门店在哪" | 需要联系方式 | `getContactInfo("sales")` |
| "现在有什么优惠" | 需要查询活动 | `getPromotions("general")` |
| "你好，你能帮我什么" | 打招呼，不需要工具 | `greet()` |
| "这个车油耗怎么样" | RAG 上下文可回答 | 直接回答（不调用工具） |

---

### 7️⃣ 答案持久化（QAService）

```java
// 步骤3：创建 answer 实体
Answer answerEntity = Answer.builder()
        .id(UUID.randomUUID())
        .questionId(question.getId())        // 1:1 关联 question
        .answer(answer.getAnswer())          // AI 生成的答案（可能融合了工具结果）
        .confidence(answer.getConfidence())
        .build();

// 步骤4：序列化引用来源为 JSON
answerEntity.setSources(objectMapper.writeValueAsString(answer.getSources()));
answerMapper.insert(answerEntity);

// 步骤5：更新 question 状态
question.setStatus(Question.Status.ANSWERED);
questionMapper.updateById(question);
```

**Answer 实体**（表名：`answer`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 主键 |
| `question_id` | UUID | 关联 question 表（1:1，外键） |
| `answer` | String | AI 生成的答案正文 |
| `sources` | TEXT(JSON) | 引用来源 JSON 数组 |
| `confidence` | Float | 整体置信度（0.0~1.0） |
| `created_at` | LocalDateTime | 自动填充 |

---

### 8️⃣ 返回响应

**`AnswerResponse` DTO**：

```java
public class AnswerResponse {
    private String answer;              // AI 生成的答案文本
    private List<SourceInfo> sources;   // 引用来源列表
    private Float confidence;           // 整体置信度

    public static class SourceInfo {
        private UUID documentId;         // 来源文档 ID
        private String documentTitle;    // 来源文档标题
        private String chunkContent;     // 片段内容（截断到 200 字符）
        private Float confidence;        // 来源置信度
    }
}
```

**HTTP 响应示例**：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {
    "answer": "秦PLUS DM-i 目前有5个配置版本可以选哦～\n\n📋 55km领先型：\n   指导价：7.98万\n   落地总价：约9.36万\n\n...",
    "sources": [
      {
        "documentId": "a0967b38-...",
        "documentTitle": "秦PLUS车型手册",
        "chunkContent": "秦PLUS DM-i 2024款...",
        "confidence": 0.85
      }
    ],
    "confidence": 0.8
  }
}
```

---

### 9️⃣ 对话历史与多轮上下文

**接口**：`GET /api/qa/sessions/{sessionId}/messages`

**上下文注入原理**：

```
SessionId=f47ac10b...

第1轮："汉兰达有什么配置"         ──→ AgentService ──→ 这是7座SUV，分精英/豪华/尊贵/至尊...
                                     │
第2轮："最低配落地多少钱"           │  loadHistory(sessionId)
                                     │  → [User: "汉兰达有什么配置",
                                     │     Assistant: "这是7座SUV，分精英/豪华/尊贵/至尊..."]
                                     │  → new UserMessage("最低配落地多少钱")
                                     │
                                     └─→ ChatClient.tools(...)
                                           └─ LLM 结合历史理解："最低配"指的是汉兰达
                                               → function_call: calculateOnRoadPrice("汉兰达")
                                               → 返回 精英版5座 落地价
```

**代码**：

```java
if (sessionId != null) {
    List<Question> history = questionMapper.findBySessionIdOrderByCreatedAt(sessionId);
    for (Question q : history) {
        Answer a = answerMapper.findByQuestionId(q.getId()).orElse(null);
        messages.add(new UserMessage(q.getQuestion()));
        if (a != null && a.getAnswer() != null) {
            messages.add(new AssistantMessage(a.getAnswer()));
        }
    }
}
```

---

### 0️⃣ 会话管理

**接口一览**：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/qa/sessions` | 获取当前用户所有会话列表 |
| `GET` | `/api/qa/sessions/{id}/messages` | 获取指定会话的所有问答消息 |
| `DELETE` | `/api/qa/sessions/{id}` | 删除会话（级联删除所有问答） |
| `GET` | `/api/qa/history?page=0&pageSize=10` | 分页获取提问历史 |

**删除会话的级联逻辑**：

```java
@Transactional
public void deleteSession(UUID sessionId) {
    // 1. 查询该会话下所有 question ID
    List<UUID> questionIds = questionMapper.findQuestionIdsBySessionId(sessionId);
    // 2. 先删除所有关联的 answer（解除外键约束）
    if (!questionIds.isEmpty()) {
        answerMapper.deleteByQuestionIds(questionIds);
    }
    // 3. 再删除所有 question
    questionMapper.deleteBySessionId(sessionId);
}
```

---

## Tool Calling 详解

### 3.1 @Tool 注解机制

Spring AI 1.1.6 的 `@Tool` 注解将任意 Java 方法声明为 LLM 可调用的函数。框架通过反射解析方法签名，自动生成 OpenAI Function Calling 所需的 JSON Schema。

```java
@Component
public class CarPriceCalculator {

    @Tool(description = "计算某款车型所有配置的落地价格，包含购置税、保险、上牌费等")
    public String calculateOnRoadPrice(
            @ToolParam(description = "车型名称，如秦PLUS、凯美瑞、汉兰达等") String carModel) {
        // ... 价格计算逻辑
    }

    @Tool(description = "计算分期购车的月供金额，含不同首付比例和贷款年限的组合方案")
    public String calculateMonthlyPayment(
            @ToolParam(description = "车型名称") String carModel,
            @ToolParam(description = "首付比例，如0.3表示30%首付") double downPaymentRatio,
            @ToolParam(description = "贷款年限（1-5年）") int years) {
        // ... 月供计算逻辑（等额本息公式）
    }
}
```

```java
@Component
public class AutomotiveAssistantTools {

    @Tool(description = "回答问候语或能力咨询，介绍助手能提供的服务")
    public String greet() { /* ... */ }

    @Tool(description = "获取联系方式、门店地址、试驾预约、售后热线等信息。" +
            "调用时机：用户询问怎么联系、电话、地址、门店、试驾预约、售后时调用")
    public String getContactInfo(
            @ToolParam(description = "咨询类型：sales(销售)、afterSales(售后)、testDrive(试驾)")
            String type) { /* ... */ }

    @Tool(description = "查询当前的优惠活动、促销、降价、金融方案、置换补贴、购置税政策等")
    public String getPromotions(
            @ToolParam(description = "咨询类型：loan(贷款)、tradeIn(置换补贴)、general(一般活动)")
            String category) { /* ... */ }
}
```

### 3.2 JSON Schema 自动生成

`@Tool` + `@ToolParam` 的元数据 → 框架自动生成 → 随 API 请求发送给 LLM。

以 `calculateOnRoadPrice` 为例，LLM 实际接收到的 Function Schema：

```json
{
  "type": "function",
  "function": {
    "name": "calculateOnRoadPrice",
    "description": "计算某款车型所有配置的落地价格，包含购置税、保险、上牌费等",
    "parameters": {
      "type": "object",
      "properties": {
        "carModel": {
          "type": "string",
          "description": "车型名称，如秦PLUS、凯美瑞、汉兰达等"
        }
      },
      "required": ["carModel"]
    }
  }
}
```

**`description` 的写法直接影响 LLM 调用准确率**：

```
❌ 坏：description = "查询价格"
     → LLM 不知道何时该用，可能乱调或从不调

✅ 好：description = "计算某款车型所有配置的落地价格，包含购置税、保险、上牌费等。"
                    + "调用时机：用户询问某车型落地价、上路价、全款总价时调用"
     → LLM 精确理解使用场景，正确触发
```

### 3.3 LLM 函数调用多轮协作

LLM 调用工具不是一次完成，而是**多轮请求-响应**：

```
┌──────────────────────────────────────────────────────────────┐
│ 第1轮：Client → LLM                                           │
│                                                              │
│ POST /v1/chat/completions                                    │
│ {                                                            │
│   "messages": [                                              │
│     {"role":"system","content":"你是汽车销售顾问..."},        │
│     {"role":"user","content":"【RAG文档】...\n秦PLUS落地多少钱"}│
│   ],                                                         │
│   "tools": [                                                 │
│     {                                                        │
│       "type": "function",                                    │
│       "function": {                                          │
│         "name": "calculateOnRoadPrice",                      │
│         "description": "计算某款车型...",                     │
│         "parameters": {...}                                  │
│       }                                                      │
│     },                                                       │
│     { "function": { "name": "greet", ... } },                │
│     { "function": { "name": "getContactInfo", ... } },       │
│     ...                                                      │
│   ]                                                          │
│ }                                                            │
│                                                              │
│ LLM 返回（不是文本！是 tool_calls 指令）：                     │
│ {                                                            │
│   "role": "assistant",                                       │
│   "tool_calls": [{                                           │
│     "id": "call_abc123",                                     │
│     "type": "function",                                      │
│     "function": {                                            │
│       "name": "calculateOnRoadPrice",                        │
│       "arguments": "{\"carModel\":\"秦PLUS\"}"               │
│     }                                                        │
│   }]                                                         │
│ }                                                            │
│                                                              │
│ ChatClient 自动执行 → calculateOnRoadPrice("秦PLUS")          │
│ 获得结果 → "【秦PLUS】落地价格明细：\n📋 55km领先型：..."       │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ 第2轮：Client → LLM（ChatClient 自动发起）                     │
│                                                              │
│ {                                                            │
│   "messages": [                                              │
│     ...原有消息...,                                           │
│     {"role":"assistant","tool_calls":[...]},                 │
│     {"role":"tool","tool_call_id":"call_abc123",              │
│      "content":"【秦PLUS】落地价格明细：\n📋 55km领先型：..."} │
│   ]                                                          │
│ }                                                            │
│                                                              │
│ LLM 返回最终自然语言文本：                                     │
│ "秦PLUS DM-i 目前有5款配置可选，帮您算一下落地价哈～\n\n..."    │
└──────────────────────────────────────────────────────────────┘
```

### 3.4 工具类清单

| 工具类 | 包路径 | @Tool 方法 | 功能 |
|--------|--------|-----------|------|
| `CarPriceCalculator` | `tool/CarPriceCalculator.java` | `calculateOnRoadPrice(carModel)` | 落地价计算（购置税+保险+上牌） |
| `CarPriceCalculator` | 同上 | `calculateMonthlyPayment(carModel, ratio, years)` | 月供计算（等额本息） |
| `AutomotiveAssistantTools` | `tool/AutomotiveAssistantTools.java` | `greet()` | 问候与能力介绍 |
| `AutomotiveAssistantTools` | 同上 | `getContactInfo(type)` | 联系方式/门店/售后/试驾 |
| `AutomotiveAssistantTools` | 同上 | `getPromotions(category)` | 优惠活动/金融方案/置换补贴 |

---

## MCP 协议集成

### 4.1 MCP 三层架构

MCP (Model Context Protocol) 是 Anthropic 提出的 **AI 工具互操作标准协议**，允许任何兼容的 AI 客户端自动发现并调用服务端暴露的工具。

```
┌──────────────────────────────────────────────────────┐
│  Client/Server Layer                                 │
│  ┌──────────┐           ┌───────────────────────┐    │
│  │ McpClient │◄──JSON-RPC──►│ McpServer              │   │
│  │ (外部AI)  │ 2.0       │ (本项目)                │   │
│  └──────────┘           │ - 工具暴露              │   │
│                         │ - 资源管理              │   │
├─────────────────────────┴───────────────────────────┤
│  Session Layer                                      │
│  McpSession: 通信模式管理、连接状态维护                 │
├─────────────────────────────────────────────────────┤
│  Transport Layer                                    │
│  STDIO | SSE | Streamable-HTTP | Stateless-HTTP     │
└─────────────────────────────────────────────────────┘
```

### 4.2 @McpTool 注解

将内部 `@Tool` 方法适配为 MCP 协议标准接口：

```java
@Component
public class McpToolsAdapter {

    @McpTool(name = "calculate-on-road-price",
            description = "计算某款车型所有配置的落地价格，包含购置税、保险、上牌费等。" +
                    "调用时机：用户询问某车型落地价、上路价、全款总价时调用")
    public String calculateOnRoadPrice(
            @McpToolParam(description = "车型名称，如秦PLUS、凯美瑞、汉兰达等") String carModel) {
        return priceCalculator.calculateOnRoadPrice(carModel);
    }

    @McpTool(name = "calculate-monthly-payment",
            description = "计算分期购车的月供金额...")
    public String calculateMonthlyPayment(
            @McpToolParam(description = "车型名称") String carModel,
            @McpToolParam(description = "首付比例，如0.3表示30%首付") double downPaymentRatio,
            @McpToolParam(description = "贷款年限（1-5年）") int years) {
        return priceCalculator.calculateMonthlyPayment(carModel, downPaymentRatio, years);
    }

    @McpTool(name = "get-contact-info", description = "获取联系方式、门店地址...")
    public String getContactInfo(
            @McpToolParam(description = "咨询类型", required = false) String type) {
        return assistantTools.getContactInfo(type);
    }

    @McpTool(name = "get-promotions", description = "查询当前的优惠活动...")
    public String getPromotions(
            @McpToolParam(description = "咨询类型", required = false) String category) {
        return assistantTools.getPromotions(category);
    }

    @McpTool(name = "greet", description = "回答问候语或能力咨询...")
    public String greet() {
        return assistantTools.greet();
    }
}
```

**`@Tool` vs `@McpTool` 的关系**：

| 维度 | `@Tool` | `@McpTool` |
|------|---------|-----------|
| 消费方 | 本项目 ChatClient 内部 | 外部 MCP 客户端（Claude Desktop 等） |
| 协议 | OpenAI Function Calling | MCP JSON-RPC 2.0 |
| 注解包 | `org.springframework.ai.tool.annotation` | `org.springaicommunity.mcp.annotation` |
| 端口 | 无需额外端口 | `/mcp` 端点 |

### 4.3 协议交互时序

```
外部 MCP 客户端                  本项目 MCP Server

     │── initialize ─────────────────────►│  握手：协商协议版本和能力
     │   {"protocolVersion":"2024-11-05"} │
     │                                     │
     │◄─ capabilities ───────────────────│  返回：支持 tools 功能
     │   {"capabilities":{"tools":{}}}    │
     │                                     │
     │── tools/list ──────────────────────►│  发现：获取所有工具
     │                                     │
     │◄─ tool list ──────────────────────│  返回 @McpTool 定义的全部工具
     │   [                                  │  含自动生成的 JSON Schema
     │     {"name":"calculate-on-road-price",
     │      "description":"计算某款车型...",
     │      "inputSchema":{"type":"object",
     │        "properties":{"carModel":...}}},
     │     {"name":"get-promotions",...},
     │     {"name":"greet",...}
     │   ]                                  │
     │                                     │
     │── tools/call ──────────────────────►│  调用：执行具体工具
     │   {"name":"calculate-on-road-price",│
     │    "arguments":{"carModel":"汉兰达"}}│
     │                                     │
     │◄─ tool result ─────────────────────│  返回工具执行结果
     │   {"content":[{"type":"text",        │
     │     "text":"【汉兰达】落地价格明细..."}]}│
```

### 4.4 依赖与配置

**pom.xml**：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

依赖传递：`spring-ai-starter-mcp-server-webmvc` → `spring-ai-autoconfigure-mcp-server-webmvc` → `spring-ai-mcp` → `spring-ai-mcp-annotations` → `mcp-annotations:0.9.0`

**application.yml**：

```yaml
spring.ai.mcp.server:
  protocol: STREAMABLE           # Streamable-HTTP 协议，支持双向流式通信
  annotation:
    enabled: true                # 启用 @McpTool 注解扫描
    base-packages: com.example.rag.mcp  # 限定扫描包
```

---

## Spring AI 框架详解

### 接口体系

```
                     Model<T, R>
                   (泛型接口基类)
                         │
          ┌──────────────┼──────────────┐
          │              │              │
    ChatModel      EmbeddingModel   ImageModel
  (聊天模型)       (嵌入模型)       (图像模型)
```

### ChatModel —— 聊天模型

```java
public interface ChatModel extends Model<Prompt, ChatResponse> {
    ChatResponse call(Prompt prompt);
    // + 流式方法
}
```

**本项目两种调用方式**：

| 方式 | 代码 | 场景 |
|------|------|------|
| 旧版直接调用 | `chatModel.call(prompt).getResult().getOutput().getText()` | 无工具调用的 RAG 问答 |
| **新版 Fluent API** | `ChatClient.builder(chatModel).build().prompt().messages().tools().call().content()` | **带工具调用的 RAG 问答** |

### EmbeddingModel —— 嵌入模型

```java
public interface EmbeddingModel extends Model<EmbeddingRequest, EmbeddingResponse> {
    float[] embed(String text);            // 单文本嵌入
    List<float[]> embed(List<String> texts); // 批量嵌入
}
```

### Prompt / Message 体系

```
Prompt（提示词）
  └─ List<Message>（消息列表）
      ├─ SystemMessage（系统消息）      → role: "system"
      ├─ UserMessage（用户消息）        → role: "user"
      ├─ AssistantMessage（助手消息）   → role: "assistant"
      └─ ToolResponseMessage（工具消息）→ role: "tool"
```

### application.yml 配置

```yaml
spring.ai:
  openai:
    api-key: sk-xxx                          # DeepSeek API Key
    base-url: https://api.deepseek.com       # ← 路由到 DeepSeek（而非 OpenAI）
    chat:
      options:
        model: deepseek-chat                 # 模型名
        temperature: 0.7                     # 创造性控制
    embedding:
      enabled: false                         # 关闭 OpenAI embedding

spring.ai.mcp.server:
  protocol: STREAMABLE                       # MCP 传输协议
  annotation:
    enabled: true
    base-packages: com.example.rag.mcp       # MCP 注解扫描范围
```

### 可移植性

切换模型只需改配置，**代码零修改**：

```yaml
# DeepSeek → Azure OpenAI
spring.ai.openai.base-url: https://xxx.openai.azure.com
spring.ai.openai.chat.options.model: gpt-4
```

---

## 容错与降级策略

本项目设计了**三层容错**机制：

```
┌─────────────────────────────────────────────┐
│ Layer 1: @Retryable + 指数退避               │
│                                             │
│ @Retryable(                                 │
│     maxAttempts = 3,                        │
│     backoff = @Backoff(                     │
│         delay = 1000,    // 首次等待 1s      │
│         multiplier = 2   // 每次翻倍         │
│     )                                       │
│ )                                           │
│ // 执行序列：第1次 → 等1s → 第2次 → 等2s → 第3次│
├─────────────────────────────────────────────┤
│ Layer 2: @Recover 降级恢复                   │
│                                             │
│ 3次重试全失败 → 自动调用 @Recover 方法       │
│ → 记录 WARN 日志                             │
│ → 返回 null                                 │
├─────────────────────────────────────────────┤
│ Layer 3: fallbackAnswer() 业务降级           │
│                                             │
│ agentService.answer() 收到 null →            │
│ "未找到与您问题直接相关的文档内容..."          │
│ confidence = 0.5                            │
└─────────────────────────────────────────────┘
```

**降级答案生成**：

```java
private AnswerResponse fallbackAnswer(String question, List<AnswerResponse.SourceInfo> sources) {
    String answer = "根据知识库中的文档内容，以下是相关信息：\n\n";
    if (!sources.isEmpty()) {
        answer += sources.stream()
                .map(s -> "📄 " + s.getChunkContent())
                .collect(Collectors.joining("\n\n"));
    } else {
        answer += "未找到与您问题直接相关的文档内容，建议您重新表述问题或查看相关文档。";
    }
    return AnswerResponse.builder().answer(answer).sources(sources).confidence(0.5f).build();
}
```

---

## 核心代码文件清单

| 文件 | 路径 | 职责 |
|------|------|------|
| `QAController` | `controller/QAController.java` | HTTP 接口，JWT 鉴权，`@Valid` 校验 |
| `QAService` | `service/QAService.java` | 问答编排：持久化→Agent→持久化，会话管理 |
| `AgentService` | `service/AgentService.java` | RAG 核心 + **Tool Calling 调度** |
| `VectorService` | `service/VectorService.java` | 向量生成 + pgvector 检索 |
| `CarPriceCalculator` | `tool/CarPriceCalculator.java` | **@Tool** 落地价/月供计算 |
| `AutomotiveAssistantTools` | `tool/AutomotiveAssistantTools.java` | **@Tool** 问候/联系/优惠 |
| `McpToolsAdapter` | `mcp/McpToolsAdapter.java` | **@McpTool** MCP 协议对外暴露 |
| `DocumentProcessService` | `service/DocumentProcessService.java` | 文档拆分（Markdown 章节级）+ 向量化入库 |
| `DashScopeEmbeddingConfig` | `config/DashScopeEmbeddingConfig.java` | DashScope Embedding 配置 + 重试 |
| `QuestionRequest` | `dto/request/QuestionRequest.java` | 请求 DTO |
| `AnswerResponse` | `dto/response/AnswerResponse.java` | 响应 DTO |
| `Question` | `entity/Question.java` | question 表实体 |
| `Answer` | `entity/Answer.java` | answer 表实体 |
| `QuestionMapper` | `mapper/QuestionMapper.java` | question 表 DAO |
| `AnswerMapper` | `mapper/AnswerMapper.java` | answer 表 DAO |
| `DocumentChunkVectorMapper` | `mapper/DocumentChunkVectorMapper.java` | 向量检索 Mapper |
| `DocumentChunkVectorMapper.xml` | `resources/mapper/DocumentChunkVectorMapper.xml` | 向量检索 SQL |
| `application.yml` | `resources/application.yml` | Spring AI / DeepSeek / DashScope / MCP 配置 |
| `pom.xml` | `pom.xml` | `spring-ai-starter-model-openai` + `spring-ai-starter-mcp-server-webmvc` |

---

## 数据模型

### Question（问题实体）

```java
@TableName("question")
public class Question {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;
    private UUID userId;           // 提问用户
    private UUID sessionId;        // 多轮会话 ID
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
    private UUID questionId;       // 关联 question 表（1:1，外键）
    private String answer;         // AI 生成的答案
    private String sources;        // JSON 格式的引用来源
    private Float confidence;      // 置信度
    private LocalDateTime createdAt;
}
```

---

## 时序图（完整）

```
Client       QAController     QAService      AgentService      ChatClient+Tools    pgvector     DeepSeek
  │               │               │                │                  │                │            │
  │ POST /question│               │                │                  │                │            │
  │ ─────────────>│               │                │                  │                │            │
  │               │ JWT鉴权       │                │                  │                │            │
  │               │ askQuestion() │                │                  │                │            │
  │               │ ─────────────>│                │                  │                │            │
  │               │               │ INSERT question│                  │                │            │
  │               │               │ (PENDING)      │                  │                │            │
  │               │               │ ──────────────────────────────────────────────────>│            │
  │               │               │                                                     │            │
  │               │               │ answer(q, kbId, sId)                              │            │
  │               │               │ ───────────────>│                                 │            │
  │               │               │                │                                  │            │
  │               │               │                │ embed(question)                   │            │
  │               │               │                │ ───→ DashScope API → float[1536]  │            │
  │               │               │                │                                  │            │
  │               │               │                │ searchByCosineSimilarity()        │            │
  │               │               │                │ ────────────────────────────────>│            │
  │               │               │                │ <── top-5 DocumentChunk ─────────│            │
  │               │               │                │                                  │            │
  │               │               │                │ buildMessages()                   │            │
  │               │               │                │  • SystemMessage                  │            │
  │               │               │                │  • 多轮历史                       │            │
  │               │               │                │  • RAG上下文 + 问题                │            │
  │               │               │                │                                  │            │
  │               │               │                │ callChatClientWithTools(msgs)     │            │
  │               │               │                │ ──────────────>│                  │            │
  │               │               │                │                │                  │            │
  │               │               │                │                │ ① POST /v1/chat  │            │
  │               │               │                │                │   messages+     │            │
  │               │               │                │                │   tools 定义     │            │
  │               │               │                │                │ ───────────────────────────>│
  │               │               │                │                │                              │
  │               │               │                │                │ ② LLM返回 tool_calls         │
  │               │               │                │                │ <───────────────────────────│
  │               │               │                │                │                              │
  │               │               │                │                │ ③ 自动执行工具                │
  │               │               │                │                │   calculateOnRoadPrice()      │
  │               │               │                │                │   getPromotions()             │
  │               │               │                │                │  ...                          │
  │               │               │                │                │                              │
  │               │               │                │                │ ④ POST /v1/chat              │
  │               │               │                │                │   发回工具结果                │
  │               │               │                │                │ ───────────────────────────>│
  │               │               │                │                │                              │
  │               │               │                │                │ ⑤ LLM生成最终答案             │
  │               │               │                │                │ <───────────────────────────│
  │               │               │                │                │                              │
  │               │               │                │<─ String answer─│                  │            │
  │               │               │                │                                  │            │
  │               │               │ <─ AnswerResponse│                                 │            │
  │               │               │                │                                  │            │
  │               │               │ INSERT answer   │                                  │            │
  │               │               │ UPDATE question │                                  │            │
  │               │               │ (ANSWERED)      │                                  │            │
  │               │               │ ──────────────────────────────────────────────────>│            │
  │               │               │                                                     │            │
  │               │ <─ AnswerResp─│                                                     │            │
  │               │               │                                                     │            │
  │ <── 200 OK ───│               │                                                     │            │
```

---

## 快速测试

### 1. 获取 Token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser2024","password":"123456"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
```

### 2. 提问（触发 Tool Calling）

```bash
# 落地价计算 → LLM 自动调用 calculateOnRoadPrice
curl -s -X POST http://localhost:8080/api/qa/question \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"秦PLUS落地多少钱？"}' | python3 -m json.tool

# 月供计算 → LLM 自动调用 calculateMonthlyPayment
curl -s -X POST http://localhost:8080/api/qa/question \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"汉兰达首付3成分5年月供多少？"}' | python3 -m json.tool

# 知识问答 → LLM 基于 RAG 上下文直接回答
curl -s -X POST http://localhost:8080/api/qa/question \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"请介绍一下秦PLUS的配置参数"}' | python3 -m json.tool
```

### 3. 多轮对话测试

```bash
# 第1轮
curl -s -X POST http://localhost:8080/api/qa/question \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"汉兰达有什么配置"}' | python3 -m json.tool

# 从返回的 sessionId 继续第2轮（LLM 结合上文理解指代）
curl -s -X POST http://localhost:8080/api/qa/question \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question":"最低配落地多少钱","sessionId":"上一步返回的sessionId"}' | python3 -m json.tool
```

### 4. 会话管理

```bash
# 获取会话列表
curl -s "http://localhost:8080/api/qa/sessions" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 获取会话消息
curl -s "http://localhost:8080/api/qa/sessions/{sessionId}/messages" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 删除会话
curl -s -X DELETE "http://localhost:8080/api/qa/sessions/{sessionId}" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

### 5. 查询对话历史

```bash
curl -s "http://localhost:8080/api/qa/history?page=0&pageSize=5" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

### 6. MCP 端点验证

```bash
# MCP Server 在 /mcp 端点上监听
# 验证服务是否就绪
curl -s http://localhost:8080/mcp/health 2>/dev/null || echo "MCP 端点就绪"
```

---

> **技术栈版本**：Spring Boot 3.5.14 · Spring AI 1.1.6 · MCP Java SDK 0.18.2 · MCP Annotations 0.9.0 · JDK 21 · DeepSeek deepseek-chat · 阿里云 DashScope text-embedding-v4 · PostgreSQL + pgvector · MyBatis-Plus 3.5.7
