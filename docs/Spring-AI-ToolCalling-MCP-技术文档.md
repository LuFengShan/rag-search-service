# Spring AI 1.1.6 Tool Calling & MCP 协议——企业级实践

> 面试文档：基于真实项目，从手写正则匹配到 LLM 自主决策工具调用 + MCP 标准协议暴露

---

## 目录

1. [项目背景与重构动机](#1-项目背景与重构动机)
2. [架构全景：重构前后对比](#2-架构全景重构前后对比)
3. [核心一：Tool Calling——@Tool 声明式工具定义](#3-核心一tool-callingtool-声明式工具定义)
4. [核心二：ChatClient.tools()——LLM 自主决策调度](#4-核心二chatclienttoolsllm-自主决策调度)
5. [核心三：MCP 协议——标准化工具暴露](#5-核心三mcp-协议标准化工具暴露)
6. [故障容错：多层重试与降级](#6-故障容错多层重试与降级)
7. [设计模式与工程实践](#7-设计模式与工程实践)
8. [面试常见追问](#8-面试常见追问)

---

## 1. 项目背景与重构动机

### 1.1 业务场景

本项目是一个 **RAG（检索增强生成）** 智能问答系统，为汽车销售直播间提供 AI 助手。用户可能问：

> "秦PLUS落地多少钱？" → 需要实时价格计算
> "现在有什么优惠活动？" → 需要查询营销活动
> "你们门店地址在哪？" → 需要返回联系方式

### 1.2 旧架构的问题

```
旧方案：正则匹配 + 手工调度
┌─────────────────────────────────────────────────────────┐
│  callToolsIfNeeded(question)                            │
│    if (question.matches(".*落地多少钱.*(秦|汉|唐...)") │
│      → extractCarModel() → carPriceCalculator.calc()    │
│    if (question.matches(".*月供.*"))                    │
│      → parseDownPaymentRatio() → parseYears()           │
│                                                         │
│  skillClassifier.matchAndExecute(question)              │
│    for (AgentSkill skill : skills)                      │
│      if (skill.canHandle(question))  // 每个Skill内部正则│
│        return skill.execute(question)                    │
└─────────────────────────────────────────────────────────┘
```

**5 个核心痛点：**

| 痛点 | 说明 |
|------|------|
| **不可扩展** | 新增一个工具需要：写 Skill 类 + 写正则 + 加到调度链，O(n) 线性增长 |
| **语义盲区** | 正则只能匹配关键词，"这个车全部弄好要多少钱" 匹配不到"落地多少钱" |
| **参数提取困难** | "首付3成分5年" 需要手写正则解析，容易遗漏边界情况 |
| **紧耦合** | 工具调用逻辑与业务逻辑混杂在 AgentService 中 |
| **无法对外开放** | 工具只能服务本项目，外部 AI 应用无法发现和调用 |

---

## 2. 架构全景：重构前后对比

### 2.1 文件级变更

```
删除（旧 Skill 体系）：          新增（新 Tool/MCP 体系）：
├── skill/AgentSkill.java        ├── tool/AutomotiveAssistantTools.java
├── skill/SkillClassifier.java   │   ├── @Tool greet()
├── skill/GreetingSkill.java     │   ├── @Tool getContactInfo(type)
├── skill/ContactSkill.java      │   └── @Tool getPromotions(category)
├── skill/ActivitySkill.java     │
                                 ├── mcp/McpToolsAdapter.java
                                 │   ├── @McpTool calculateOnRoadPrice()
                                 │   ├── @McpTool calculateMonthlyPayment()
                                 │   ├── @McpTool getContactInfo()
                                 │   ├── @McpTool getPromotions()
                                 │   └── @McpTool greet()
                                 │
重构：
└── service/AgentService.java
    └── callChatClientWithTools() 替代 callToolsIfNeeded() + SkillClassifier
```

### 2.2 架构对比图

```
重构前：手动正则调度                          重构后：LLM 自主决策
                                               
用户问题                                       用户问题
  │                                              │
  ├─ 正则1: 落地价? → CarPriceCalculator         ├─ 向量检索（RAG）
  ├─ 正则2: 月供?   → CarPriceCalculator         │
  └─ 都不匹配        ↓                            ├─ 构建消息（Sys+History+RAG）
                    SkillClassifier               │
                      ├─ GreetingSkill            └─ ChatClient
                      ├─ ContactSkill                   │
                      ├─ ActivitySkill                  ├─ tools(carPriceCalculator,
                      └─ null → 走 RAG 流程              │         automotiveTools)
                                                         │
  决策方：程序员手写正则                              决策方：LLM 理解语义
  匹配粒度：关键词级别                                匹配粒度：语义级别
  扩展成本：新增类 + 写正则 + 注册                    扩展成本：加一个 @Tool 方法
```

---

## 3. 核心一：Tool Calling——@Tool 声明式工具定义

### 3.1 Spring AI Tool Calling 原理

Spring AI 1.1.6 的 Tool Calling 基于 [Function Calling](https://platform.openai.com/docs/guides/function-calling) 协议的工程化封装，核心流程图：

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  1. DEFINITION (请求阶段)                                        │
│  ┌───────────────┐    ChatClient 读取 @Tool 元数据               │
│  │ @Tool 注解方法 │ ──────────────────────────────► JSON Schema  │
│  │ name/description                                          │   │
│  │ params: @ToolParam   │    自动生成 OpenAI Function Schema  │   │
│  └───────────────┘                                          │   │
│                                                                 │
│  2. ORCHESTRATION (调度阶段)                                     │
│  ┌───────────────┐    请求发送到 LLM                  ┌──────┐  │
│  │ ChatClient     │ ───► {messages, functions} ──────►│ LLM  │  │
│  │ .tools(...)    │                                    │      │  │
│  └───────────────┘     LLM 返回 function_call          └──────┘  │
│                             │                                    │
│                             ▼                                    │
│  ┌───────────────────────────────────────────────┐              │
│  │ ToolCallingManager 内部循环：                   │              │
│  │   1. 解析 LLM 返回的 toolName + arguments      │              │
│  │   2. 通过 ToolCallbackResolver 定位 ToolCallback│             │
│  │   3. 执行 tool.call(toolContext)                │              │
│  │   4. 将结果包装为 ToolResultMessage 发回 LLM     │              │
│  │   5. 重复直到 LLM 返回 content（最多迭代上限）    │              │
│  └───────────────────────────────────────────────┘              │
│                                                                 │
│  3. RESULT (响应阶段)                                            │
│  ┌───────────────────────────────────────────┐                  │
│  │ LLM 收到工具结果 → 生成最终自然语言回答 → 返回客户端 │          │
│  └───────────────────────────────────────────┘                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 @Tool 注解能力矩阵

```java
public @interface Tool {
    String name()        default "";   // 工具名称，默认取方法名
    String description() default "";   // ⚠️ 这是给 LLM 看的，决定何时调用
    boolean returnDirect() default false; // true=直接返回结果，不再给 LLM 二次加工
    Class<? extends ToolCallResultConverter> resultConverter(); // 自定义结果转换器
}
```

**关键设计点：`description` 是 Prompt Engineering 的一部分**

`description` 直接写入 OpenAI API 的 `functions[].description` 字段，LLM 根据它判断是否调用。写法规范：

```
❌ 坏：description = "获取联系方式"
✅ 好：description = "获取联系方式、门店地址、试驾预约、售后热线等信息。" +
                    "调用时机：用户询问怎么联系、电话、地址、门店、试驾预约、售后时调用"
```

### 3.3 @ToolParam 能力矩阵

```java
public @interface ToolParam {
    String description() default "";   // 参数说明，会被写入 JSON Schema 的 description
    boolean required() default true;   // 是否必填，影响 JSON Schema 的 required 数组
}
```

**Spring AI 自动做的事：**
1. 通过反射读取方法参数类型 → 映射为 [JSON Schema type](https://json-schema.org/)
2. `@ToolParam.description` → 写入 `properties.{paramName}.description`
3. `@ToolParam.required` → 写入 `required` 数组
4. 复杂对象（POJO/Record）：递归生成嵌套 JSON Schema

### 3.4 实战代码：AutomotiveAssistantTools

```java
@Component
public class AutomotiveAssistantTools {

    // 无参数工具：问候
    @Tool(description = "回答问候语或能力咨询，介绍助手能提供的服务")
    public String greet() { /* ... */ }

    // 带参数工具：根据咨询类型返回不同联系方式
    @Tool(description = "获取联系方式、门店地址、试驾预约、售后热线等信息。" +
            "调用时机：用户询问怎么联系、电话、地址、门店、试驾预约、售后、保养、维修联系方式时调用")
    public String getContactInfo(
            @ToolParam(description = "用户咨询的具体类型：sales(销售咨询)、afterSales(售后)、testDrive(试驾)，不明确时传null")
            String type) {
        // 根据 type 参数返回不同的联系方式文案
        if ("afterSales".equalsIgnoreCase(type)) { /* 售后热线 */ }
        if ("testDrive".equalsIgnoreCase(type))  { /* 试驾预约 */ }
        return /* 默认联系方式 */;
    }

    // 带参数工具：根据类别返回不同优惠活动
    @Tool(description = "查询当前的优惠活动、促销、降价、金融方案、置换补贴、购置税政策等")
    public String getPromotions(
            @ToolParam(description = "咨询类型：loan(贷款/首付/月供)、tradeIn(置换补贴)、general(一般活动)")
            String category) {
        // 根据 category 返回不同的活动文案
    }
}
```

Spring AI 自动为 `getContactInfo` 生成如下 JSON Schema（LLM 看到的是这个）：

```json
{
  "name": "getContactInfo",
  "description": "获取联系方式、门店地址、试驾预约、售后热线等信息...",
  "parameters": {
    "type": "object",
    "properties": {
      "type": {
        "type": "string",
        "description": "用户咨询的具体类型：sales(销售咨询)、afterSales(售后)、testDrive(试驾)，不明确时传null"
      }
    },
    "required": ["type"]
  }
}
```

---

## 4. 核心二：ChatClient.tools()——LLM 自主决策调度

### 4.1 调用链路

```java
ChatClient.builder(chatModel).build()          // 1. 创建 ChatClient（Fluent API）
    .prompt()                                   // 2. 进入 prompt 构建模式
    .messages(messages)                         // 3. 设置对话消息
    .tools(carPriceCalculator, automotiveTools) // 4. 注入工具实例
    .call()                                     // 5. 发起调用
    .content();                                 // 6. 获取最终文本
```

### 4.2 内部执行流程（AgentService）

```java
@Service
public class AgentService {

    private final ChatModel chatModel;          // DeepSeek（OpenAI 兼容）
    private final CarPriceCalculator carPriceCalculator;   // @Tool 注解的工具类
    private final AutomotiveAssistantTools automotiveTools; // @Tool 注解的工具类

    /**
     * 核心回答方法
     *
     * 流程：向量检索 → 构建消息(系统+历史+RAG) → ChatClient.tools(两个工具实例)
     *
     * 关键：不需要任何 if/else 或正则判断，LLM 自己理解用户意图
     * 并返回 function_call 请求。ChatClient 内部自动执行工具并将结果
     * 反馈给 LLM，最终 LLM 融合工具结果生成自然语言回答。
     */
    public AnswerResponse answer(String question, UUID knowledgeBaseId, UUID sessionId) {
        // 步骤1: RAG 向量检索
        String ragContext = buildRagContext(question, knowledgeBaseId, sessionId);

        // 步骤2: 构建多轮对话消息列表
        List<Message> messages = buildMessages(question, ragContext, knowledgeBaseId, sessionId);

        // 步骤3: 核心调用——LLM 自主决策工具调用
        String answer = callChatClientWithTools(messages);

        // 步骤4: 组装响应
        return answer != null
                ? AnswerResponse.builder().answer(answer).confidence(0.8f).build()
                : fallbackAnswer(question, List.of());
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String callChatClientWithTools(List<Message> messages) {
        return ChatClient.builder(chatModel).build()
                .prompt()
                .messages(messages)              // 系统提示词 + 历史对话 + RAG 上下文 + 当前问题
                .tools(carPriceCalculator, automotiveTools) // ⚡ 注入全部工具
                .call()
                .content();
    }
}
```

### 4.3 LLM 如何决策——一个真实的多轮协作案例

```
用户："秦PLUS落地多少钱"

┌──────────────────────────────────────────────────────────────┐
│ 第1轮 LLM 请求                                               │
│ POST /v1/chat/completions                                    │
│ {                                                            │
│   "messages": [                                              │
│     {"role":"system","content":"你是汽车销售顾问..."},        │
│     {"role":"user","content":"【RAG文档】...\n【问题】秦PLUS..│
│   ],                                                         │
│   "functions": [                                             │
│     {                                                        │
│       "name":"calculateOnRoadPrice",                         │
│       "description":"计算某款车型所有配置的落地价格...",       │
│       "parameters":{                                         │
│         "properties":{"carModel":{"type":"string",...}}      │
│       }                                                      │
│     },                                                       │
│     {...greet, getContactInfo, getPromotions, ...}           │
│   ]                                                          │
│ }                                                            │
│                                                              │
│ LLM 返回（不是文本！是 function_call 指令）：                  │
│ {                                                            │
│   "finish_reason": "function_call",                          │
│   "function_call": {                                         │
│     "name": "calculateOnRoadPrice",                          │
│     "arguments": "{\"carModel\":\"秦PLUS\"}"                  │
│   }                                                          │
│ }                                                            │
│                                                              │
│ ChatClient 自动执行 → calculateOnRoadPrice("秦PLUS")          │
│ 获得结果 → "【秦PLUS】落地价格明细：\n📋 55km领先型：..."       │
└──────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ 第2轮 LLM 请求（ChatClient 自动发起）                          │
│ {                                                            │
│   "messages": [                                              │
│     ...原有消息...,                                           │
│     {"role":"function","name":"calculateOnRoadPrice",         │
│      "content":"【秦PLUS】落地价格明细：\n..."}                │
│   ]                                                          │
│ }                                                            │
│                                                              │
│ LLM 返回最终文本：                                            │
│ "秦PLUS DM-i 目前有5个配置版本可以选哦～\n\n..."               │
└──────────────────────────────────────────────────────────────┘
```

### 4.4 消息构建策略

```java
private List<Message> buildMessages(String question, String ragContext,
                                     UUID knowledgeBaseId, UUID sessionId) {
    List<Message> messages = new ArrayList<>();

    // 1️⃣ 系统提示词：定义角色 + 能力边界 + 输出规范
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

    // 3️⃣ RAG 上下文 + 用户问题：将检索结果拼入 user message
    messages.add(new UserMessage(
            "【检索到的相关文档内容】\n\n" + ragContext +
            "\n【用户问题】\n" + question +
            "\n\n请基于以上文档内容回答用户的问题。"));

    return messages;
}
```

---

## 5. 核心三：MCP 协议——标准化工具暴露

### 5.1 MCP (Model Context Protocol) 是什么

[MCP](https://modelcontextprotocol.org/) 是 Anthropic 提出、现已开源的标准协议，解决的是 **AI 应用与外部工具/数据源之间的互操作问题**。

```
┌──────────────────────────────────────────────────────────────────┐
│                      无 MCP 的世界                                │
│                                                                  │
│   ChatGPT ─── 自定义 API ───► 工具A                               │
│   Claude  ─── 另一套 API ──► 工具A  (每次都要写适配代码)           │
│   Copilot ─── 再一套 API ──► 工具A                                │
│                                                                  │
├──────────────────────────────────────────────────────────────────┤
│                      有 MCP 的世界                                │
│                                                                  │
│   ChatGPT ─┐                                                     │
│   Claude  ─┼── MCP 协议 ──► MCP Server ──► 工具A/工具B/工具C       │
│   Copilot ─┘     (统一标准)        (本项目)                       │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 5.2 MCP Java SDK 三层架构

```
┌──────────────────────────────────────────────┐
│  Client/Server Layer (顶层)                   │
│  ┌──────────────┐   ┌───────────────────┐    │
│  │ McpClient     │   │ McpServer          │   │
│  │ - 连接管理     │   │ - 工具暴露          │   │
│  │ - 协议协商     │   │ - 资源管理          │   │
│  │ - 工具发现     │   │ - 提示模板          │   │
│  └──────┬───────┘   └────────┬──────────┘    │
│         │                    │               │
├─────────┼────────────────────┼───────────────┤
│  Session Layer (中间层)      │               │
│  ┌──────────────────────────┐│               │
│  │ McpSession                │               │
│  │ - 通信模式管理             │               │
│  │ - 连接状态维护             │               │
│  └──────────┬───────────────┘               │
│             │                               │
├─────────────┼───────────────────────────────┤
│  Transport Layer (底层)                      │
│  ┌─────────────────────────────────────┐    │
│  │ McpTransport                         │    │
│  │ - JSON-RPC 2.0 消息序列化/反序列化    │    │
│  │ - STDIO / SSE / Streamable-HTTP     │    │
│  └─────────────────────────────────────┘    │
└──────────────────────────────────────────────┘
```

### 5.3 本项目 MCP 配置

**依赖（pom.xml）：**

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

这个 starter 的依赖传递链：

```
spring-ai-starter-mcp-server-webmvc
  ├── spring-ai-autoconfigure-mcp-server-webmvc  (自动配置)
  ├── spring-ai-autoconfigure-mcp-server-common   (通用配置)
  ├── spring-ai-mcp                               (核心 MCP 逻辑)
  └── spring-ai-mcp-annotations                   (@McpTool 等注解)
        └── org.springaicommunity:mcp-annotations:0.9.0  (注解实现)
```

**配置（application.yml）：**

```yaml
spring.ai.mcp.server:
  protocol: STREAMABLE       # 传输协议：Streamable-HTTP
  annotation:
    enabled: true            # 启用注解扫描
    base-packages: com.example.rag.mcp  # 限定扫描包路径
```

**传输协议选择：**

| 协议 | 适用场景 | 端口 |
|------|---------|------|
| `STDIO` | 本地进程间通信（Claude Desktop 默认） | 无 |
| `SSE` | 单向流式推送 | `/mcp` |
| `STREAMABLE` | ⭐ **双向流式通信（本项目使用）** | `/mcp` |
| `STATELESS` | 无状态请求-响应 | `/mcp` |

选择 `STREAMABLE` 的理由：支持 HTTP 远程调用、双向通信、天然适合 Web 部署。

### 5.4 @McpTool 实战代码

```java
@Component
public class McpToolsAdapter {

    private final CarPriceCalculator priceCalculator;
    private final AutomotiveAssistantTools assistantTools;

    /**
     * ⚠️ @McpTool vs @Tool 的关键区别：
     *
     * @Tool  → 对内（本项目 AgentService 的 ChatClient 调用）
     * @McpTool → 对外（任何 MCP 客户端通过协议发现并调用）
     *
     * 两者可共存于同一项目，MCP 端点多了一层协议序列化/反序列化。
     */
    @McpTool(name = "calculate-on-road-price",
            description = "计算某款车型所有配置的落地价格，包含购置税、保险、上牌费等。" +
                    "调用时机：用户询问某车型落地价、上路价、全款总价时调用")
    public String calculateOnRoadPrice(
            @McpToolParam(description = "车型名称，如秦PLUS、凯美瑞、汉兰达等")
            String carModel) {
        return priceCalculator.calculateOnRoadPrice(carModel);
    }

    @McpTool(name = "get-promotions",
            description = "查询当前的优惠活动、促销、降价、金融方案、置换补贴、购置税政策等。")
    public String getPromotions(
            @McpToolParam(description = "咨询类型：loan(贷款)、tradeIn(置换补贴)、general(一般活动)",
                    required = false)
            String category) {
        return assistantTools.getPromotions(category);
    }

    // 其余工具同理...
}
```

### 5.5 MCP 协议交互时序

```
Client (如 Claude Desktop)              MCP Server (本项目)

     │── POST /mcp ──────────────────────────►│
     │   {"jsonrpc":"2.0",                    │
     │    "method":"initialize",              │
     │    "params":{                          │ 协议握手：协商版本和能力
     │      "protocolVersion":"2024-11-05",   │
     │      "capabilities":{...}}}            │
     │                                        │
     │◄─ 200 OK ────────────────────────────│
     │   {"result":{                          │
     │     "protocolVersion":"2024-11-05",    │ 返回服务器能力：tools, resources
     │     "serverInfo":{"name":"rag-search"} │
     │     "capabilities":{"tools":{}}}}      │
     │                                        │
     │── POST /mcp ──────────────────────────►│
     │   {"jsonrpc":"2.0",                    │
     │    "method":"tools/list"}              │ 工具发现：获取所有工具列表
     │                                        │
     │◄─ 200 OK ────────────────────────────│
     │   {"result":{"tools":[                 │
     │     {"name":"calculate-on-road-price", │ 返回 @McpTool 定义的全部工具
     │      "description":"计算某款车型...",   │ 含自动生成的 JSON Schema
     │      "inputSchema":{...}},             │
     │     {"name":"get-promotions", ...},     │
     │     {"name":"greet", ...}              │
     │   ]}}                                  │
     │                                        │
     │── POST /mcp ──────────────────────────►│
     │   {"jsonrpc":"2.0",                    │
     │    "method":"tools/call",              │ 工具调用
     │    "params":{                          │
     │      "name":"calculate-on-road-price", │
     │      "arguments":{"carModel":"汉兰达"}}}│
     │                                        │
     │◄─ 200 OK ────────────────────────────│
     │   {"result":{"content":[               │
     │     {"type":"text",                    │ 返回工具执行结果
     │      "text":"【汉兰达】落地价格明细..."}]}}│
```

---

## 6. 故障容错：多层重试与降级

### 6.1 三层防护体系

```
┌─────────────────────────────────────────────────────────┐
│ Layer 1: Spring @Retryable（指数退避重试）               │
│                                                         │
│ @Retryable(                                             │
│     maxAttempts = 3,                                    │
│     backoff = @Backoff(delay = 1000, multiplier = 2)    │
│ )                                                       │
│ // 第1次失败 → 等 1s → 第2次 → 等 2s → 第3次            │
│                                                         │
├─────────────────────────────────────────────────────────┤
│ Layer 2: @Recover（降级恢复方法）                         │
│                                                         │
│ 3次重试全失败后自动调用 @Recover 方法                     │
│ → 返回 null 通知上层进入 fallback                         │
│                                                         │
├─────────────────────────────────────────────────────────┤
│ Layer 3: fallbackAnswer()（业务降级）                     │
│                                                         │
│ "未找到与您问题直接相关的文档内容，建议重新表述..."        │
│ confidence = 0.5（标记为低置信度）                        │
└─────────────────────────────────────────────────────────┘
```

### 6.2 @Retryable + @Recover 原理

```java
// Spring AOP 在运行时为 @Retryable 方法创建代理
// 拦截异常 → 等待 backoff → 重新调用 → 超过 maxAttempts 则调用 @Recover

@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
public String callChatClientWithTools(List<Message> messages) {
    return ChatClient.builder(chatModel).build()
            .prompt().messages(messages)
            .tools(carPriceCalculator, automotiveTools)
            .call().content();
}

@Recover
public String callChatClientWithToolsFallback(Exception e, List<Message> messages) {
    log.warn("ChatClient+Tools call failed after retries: {}", e.getMessage());
    return null;  // 返回 null 触发上层 fallbackAnswer()
}
```

**需要启用 `@EnableRetry`（在 Spring Boot 配置类或 application 启动类上）。**

---

## 7. 设计模式与工程实践

### 7.1 模式一览

| 设计模式 | 应用位置 | 说明 |
|---------|---------|------|
| **适配器模式** | `McpToolsAdapter` | 将内部 `@Tool` 方法适配为 MCP 标准接口 |
| **策略模式** | `selectSystemPrompt()` | 根据知识库类型（CAR_MD/默认）选择不同 System Prompt |
| **建造者模式** | `ChatClient.builder()` | Fluent API 链式构建调用参数 |
| **代理模式** | `@Retryable` AOP | Spring AOP 为方法创建重试代理 |
| **模板方法** | `buildMessages()` | 固定流程：System → History → RAG → Question |
| **降级模式** | `@Recover` + `fallbackAnswer()` | 多层容错，优雅降级 |

### 7.2 扩展性验证

新增一个"库存查询"工具，只需要：

```java
// 1. 在 AutomotiveAssistantTools 或新类中加一个方法
@Tool(description = "查询某车型当前库存情况，包括颜色、数量、预计到店时间")
public String checkInventory(
        @ToolParam(description = "车型名称") String carModel,
        @ToolParam(description = "颜色偏好") String color) {
    return inventoryService.query(carModel, color);
}

// 2. 在 McpToolsAdapter 中暴露给外部
@McpTool(name = "check-inventory",
        description = "查询某车型当前库存情况...")
public String checkInventory(
        @McpToolParam(description = "车型名称") String carModel,
        @McpToolParam(description = "颜色偏好", required = false) String color) {
    return assistantTools.checkInventory(carModel, color);
}

// 3. 在 AgentService.callChatClientWithTools() 的 .tools() 中加一个参数
//    .tools(carPriceCalculator, automotiveTools, inventoryTools)

// ⚠️ 不需要修改任何调度逻辑！不需要写正则！LLM 自主理解何时调用。
```

### 7.3 性能考量

| 维度 | 说明 |
|------|------|
| **工具调用增加延迟** | 每个 function_call 多一轮 LLM 请求，约 +500ms ~ +2s |
| **Token 消耗** | 工具定义 JSON Schema 会占用 context window |
| **优化建议** | 压缩 description 长度；合理使用 `returnDirect` 跳过一次回传 |

---

## 8. 面试常见追问

### Q1: `@Tool` 和 `@McpTool` 到底有什么区别？

> **答**：`@Tool` 是 Spring AI 的 **Tool Calling** API，用于本项目的 `ChatClient` 内部调用，基于 OpenAI Function Calling 协议。`@McpTool` 是 MCP（Model Context Protocol）标准协议的注解实现，用于将工具暴露给**外部**的 MCP 客户端。两者底层都是通过反射解析方法签名自动生成 JSON Schema，只是面向的消费方不同——一个是 LLM 本身，一个是外部 AI 应用。

### Q2: Tool Calling 的执行循环会不会无限递归？

> **答**：不会。Spring AI 的 `ToolCallingManager` 有最大迭代次数限制（默认 16 次）。实际中 LLM 通常在 1-2 轮内返回最终内容。如果达到上限仍无结果，框架会返回已累积的内容或抛出异常。

### Q3: 为什么不直接用 OpenAI 的 Function Calling API，而要通过 Spring AI？

> **答**：
> 1. **解耦**：Spring AI 抽象了 `ChatModel` 接口，底层可以无障碍切换 DeepSeek/Qwen/GPT/Claude
> 2. **工程化**：`@Tool` 注解 + 自动 JSON Schema 生成节省大量重复代码
> 3. **生态集成**：与 Spring Boot 的 DI、AOP、Actuator 无缝配合
> 4. **MCP 支持**：一套注解同时服务 Tool Calling 和 MCP 两个场景

### Q4: MCP 的 `STREAMABLE` 协议和传统的 REST API 有什么区别？

> **答**：MCP 基于 **JSON-RPC 2.0**，是方法级调用协议而非资源级。REST 面向资源 CRUD（GET/POST/PUT/DELETE `/tools/123`），MCP 面向方法调用（`tools/list`、`tools/call`）。`STREAMABLE` 支持双向流式传输，AI 客户端可以持续推送消息而无需轮询。

### Q5: 如果工具执行报错了怎么办？

> **答**：Spring AI 的 Tool Calling 框架会捕获工具执行异常，构造一个 **错误消息**（而不是抛给上层）发回 LLM。LLM 收到错误消息后会尝试：1）重新理解参数 2）换一种方式调用 3）告知用户无法完成。同时，本项目的 `@Retryable` 在框架层提供额外的重试容错。

### Q6: System Prompt 中的"可用工具"提示是必需的吗？

> **答**：不是强制必需的。Tool Calling 本质上是将工具定义作为 API 参数发给 LLM，LLM 不需要在 System Prompt 中看到工具列表就能调用。但加上"你可以调用可用的工具函数来获取实时数据"这句话，有助于 LLM 更积极地使用工具而非猜测数据，提高工具调用率。

---

## 附录：技术栈版本

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.5.14 |
| Spring AI | 1.1.6 |
| MCP Java SDK | 0.18.2 |
| MCP Annotations | 0.9.0 |
| JDK | 21 |
| DeepSeek | OpenAI 兼容协议 |
| PostgreSQL | 向量存储 |
| MyBatis-Plus | 3.5.7 |

---

> 本文档基于实际工程重构总结，覆盖了从手动正则调度到 LLM 自主决策 + MCP 标准协议的全链路技术细节。
