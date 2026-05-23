# 面试题：RAG 知识库智能问答系统

---

## 一、架构与设计

### 1. 请介绍一下这个项目的整体架构，以及为什么选择这些技术栈？

**考察点**：对项目全局的理解，技术选型是否有自己的思考。

期望涉及：Spring Boot 3.2 + MyBatis-Plus + PostgreSQL/pgvector + Spring AI + DeepSeek + DashScope Embedding。为什么用 MyBatis-Plus 而不是 JPA？为什么用 pgvector 而不是独立的向量数据库（如 Milvus、Pinecone）？

### 2. 这个项目的核心业务流程是什么？从用户上传文档到可以问答，中间经历了哪些步骤？

**考察点**：对 RAG 全链路的理解。

期望答出：上传 → 解析（Tika）→ 分块（智能分块策略）→ 向量化（Embedding）→ 存入 pgvector → 用户提问 → 向量检索 → 拼装 Prompt → LLM 生成回答。

### 3. 文档处理为什么设计成异步的？你是怎么做异步处理的？

**考察点**：实际工程问题的解决能力。

期望涉及：`@Async` + 自定义线程池 `documentProcessExecutor`（core=2, max=4, queue=100）。不阻塞 HTTP 请求，文档可能很大、解析和向量化耗时较长。状态机设计（UPLOADING → PROCESSING → INDEXED/FAILED）让前端可以轮询进度。

### 4. 如果文档上传量突然暴增，线程池队列满了怎么办？

**考察点**：对异步处理的深入理解、背压处理。

可能的答案：ThreadPoolExecutor 的拒绝策略（默认 AbortPolicy 抛异常，可改为 CallerRunsPolicy 让调用线程执行）、监控队列深度、水平扩展。

---

## 二、RAG 核心

### 5. 你的文档分块（Chunking）策略是怎样的？为什么这么设计？

**考察点**：对 RAG 检索质量的思考。

期望答出：两种策略——
- `smartChunkContent`：按段落分割（`\n{2,}`），50-500 字符为目标大小，短段落尝试合并，长段落按句子拆分后再聚合。
- `markdownSectionChunk`：识别 frontmatter 和 `##` 标题结构，保持章节完整性。

为什么不直接用固定长度切分？→ 固定长度会切断语义单元，影响检索质量。

### 6. 分块大小为什么选 500 字符？太大或太小会有什么问题？

**考察点**：对 chunk size 权衡的理解。

太大：检索精度下降，一个 chunk 包含太多无关信息，embedding 语义被稀释。
太小：丢失上下文，一个完整语义被拆散，LLM 拿到的片段不完整。
500 是一个经验值，在这个场景下和 embedding 模型的上下文窗口匹配较好。

**补充：Embedding 模型的上下文窗口是什么意思？**

Embedding 模型的**上下文窗口**是指模型在一次处理中能够接受的最大输入文本长度（以 token 为单位）。

- **作用**：决定了文档分块的上限，超过窗口的文本会被截断
- **与 LLM 窗口的区别**：Embedding 窗口是针对单个文档片段的，LLM 窗口是针对整个对话上下文的
- **Token 换算**：中文约 1.5-2 字符 = 1 token，英文约 4-5 字符 = 1 token
- **项目实践**：使用 DashScope text-embedding-v2（2048 token 窗口），500 中文字符 ≈ 250-350 tokens，留有充足安全余量

> 详细学习文档：[Embedding模型上下文窗口详解.md](./Embedding模型上下文窗口详解.md)

### 7. 向量检索用了 pgvector 的哪种距离算法？为什么选它？

**考察点**：对向量相似度算法的理解。

项目中默认用余弦相似度（`<=>`）。余弦相似度关注方向而非大小，适合文本语义相似度比较。L2 距离受向量长度影响大，内积适合需要同时考虑方向和强度的场景（如推荐系统）。

### 8. pgvector 的向量检索性能如何？如果数据量到百万级别怎么办？

**考察点**：对向量数据库的性能认知。

pgvector 支持 IVFFlat 索引（需创建索引并指定 lists 参数）。百万级别在建立 IVFFlat 索引后，检索可控制在几十毫秒。如果到千万/亿级别，可能需要考虑专用向量数据库（Milvus、Qdrant）或近似检索（HNSW）。

### 9. 你如何处理检索结果不够相关的情况？有哪些优化方向？

**考察点**：对 RAG 质量优化的认知。

可能的答案：优化分块策略、调整 chunk size、尝试不同的 embedding 模型、混合检索（关键词 + 向量）、重排序（Reranker）、丰富文档元数据做过滤、调整检索的 top_k、HyDE（Hypothetical Document Embeddings）。

---

## 三、LLM 集成

### 10. 你是怎么集成 DeepSeek 大模型的？如果换成其他模型（如 GPT-4、文心一言）需要改多少代码？

**考察点**：对 Spring AI 抽象层的理解。

通过 Spring AI 的 `ChatModel` 接口 + OpenAI 兼容协议。DeepSeek 的 API 和 OpenAI 协议兼容，所以配置 `spring.ai.openai.base-url` 指向 DeepSeek 即可。换模型只需改配置（API key、base-url、model name），不需要改业务代码——这就是 Spring AI 可移植性的价值。

### 11. Embedding 模型为什么没用 DeepSeek 的，而单独接了阿里云的 DashScope？

**考察点**：技术选型的判断力。

Spring AI 的 OpenAI embedding 和 DeepSeek 的 embedding 接口可能不兼容 / DeepSeek 当时 embedding 能力不成熟。所以自定义实现了 `EmbeddingModel` 接口，对接 DashScope 的兼容模式 API。这也展示了如果 Spring AI 的 starter 不满足需求，可以自己实现接口来扩展。

### 12. 你的 AgentService 里有一个工具调用（CarPriceCalculator）+ 技能匹配（SkillClassifier）+ RAG 的三级 pipeline，为什么要这样分层？

**考察点**：对 Agent 架构设计的理解。

- **工具层**（Tool）：处理需要精确计算的问题（价格、月供），不需要 LLM 参与，直接返回准确结果。
- **技能层**（Skill）：处理固定话术类问题（问候、联系方式），速度快，不需要调 LLM。
- **RAG 层**：处理开放域知识问答，需要检索 + LLM 生成。

这样设计的好处：减少不必要的 LLM 调用（省钱 + 快），对确定性问题给出确定性答案，LLM 只做它擅长的事情。

### 13. 你的 Chat History（多轮对话）是怎么实现的？有什么潜在问题？

**考察点**：对对话管理的理解，上下文窗口意识。

通过 `sessionId` 关联同一会话的多轮问答。在 `AgentService.answer()` 中，从数据库查询历史 `Question` 和 `Answer`，构造为 `UserMessage` + `AssistantMessage` 列表拼入 Prompt。

潜在问题：对话长了会超出模型的 context window；没有做历史消息的摘要/截断；每次都查全量历史。

---

## 四、安全

### 14. JWT 认证的完整流程是怎样的？token 过期了怎么办？

**考察点**：对认证机制的理解。

流程：用户登录 → 服务端验证用户名密码 → 生成 JWT（含 userId、roles，有过期时间）→ 返回给客户端 → 客户端后续请求带 `Authorization: Bearer <token>` → `JwtAuthenticationFilter` 从 Header 提取 token → 验证签名和过期时间 → 从 token 中提取 userId → 查库获取用户详情 → 设入 SecurityContext。

过期处理：当前项目设置了 24 小时过期，但缺少 refresh token 机制。生产环境一般需要双 token（短期 access token + 长期 refresh token）。

### 15. 你的文档上传有没有做安全校验？如果有人上传一个带病毒的 HTML 文件或超大文件怎么办？

**考察点**：安全意识。

文件大小：Spring 的 `multipart.max-file-size: 50MB` 有基础限流。`MaxUploadSizeExceededException` 有全局异常处理。
文件类型：通过 `isFormatAllowed()` 校验扩展名白名单，特定知识库只接受 `.md`。
不足：只校验了扩展名，没有校验文件内容的真实 MIME 类型（Magic Number）。HTML/XSS 风险在纯文本提取中基本可控（Tika 会剥离标签），但仍需关注。

---

## 五、数据库 & ORM

### 16. 为什么所有 ID 都用 UUID 而不是自增 ID？有什么优缺点？

**考察点**：对分布式系统设计的认知。

优点：可以在应用层生成，不需要数据库自增；分布式环境下 ID 不会冲突；安全性好（不可遍历）。
缺点：索引性能比自增 ID 差（B+Tree 页分裂）；存储空间大；可读性差。
这个项目用 UUID 是合理的——未来可能多实例部署。

### 17. MyBatis-Plus 的逻辑删除是怎么配置的？什么时候真正物理删除数据？

**考察点**：对框架细节的熟悉。

全局配置 `logic-delete-field: deleted`，值为 1 表示删除。MyBatis-Plus 会自动把 `deleteById()` 转为 `UPDATE SET deleted=1`，查询时自动追加 `WHERE deleted=0`。项目中 `DocumentController` 的删除是物理删除（删文件 + 删向量 + 删记录），是在 Service 层手动处理的。

### 18. pgvector 的向量列在 MyBatis 的 XML 里是怎么传入的？

**考察点**：对 pgvector 集成的理解。

Java 侧 `VectorService.arrayToString()` 把 `float[]` 转为字符串 `[0.1, 0.2, ...]`，XML 中使用 `CAST(#{embedding} AS vector)` 将字符串转为 pgvector 的 `vector` 类型。这是手工拼接的方式，不够类型安全，生产环境建议用 pgvector 的 JDBC 类型映射。

---

## 六、线上运维 & 性能

### 19. 如果 Embedding API 调用失败了怎么办？文档处理会丢吗？

**考察点**：容错设计。

当前代码：`VectorService.embed()` 在 API 失败时会 `catch` 异常，降级为基于 hash 的 mock embedding（`generateFallbackEmbedding()`）。文档不会丢失——流程会继续，但生成的是无效向量，后续检索质量会受影响。

文档处理的容错：`DocumentProcessService.processDocument()` 中的 try-catch 会捕获所有异常，将文档标记为 FAILED，不会导致整个流程崩溃。

问题：mock embedding 让问题被"悄悄"掩盖了——可以追问候选人如何改进（如重试机制、死信队列、告警通知）。

### 20. 这个系统如何部署？如果要做高可用，你会怎么改进？

**考察点**：对生产环境的理解。

当前有 `Dockerfile` + `docker-compose.yml`，单机部署。部署目录有 Nginx 反向代理、systemd 服务管理、一键部署脚本。

高可用改进方向：多实例 + 负载均衡（Nginx/Spring Cloud Gateway）、数据库主从/集群、Redis 集群、无状态服务设计（JWT 已支持）、文档上传改用对象存储（OSS/S3）。

### 21. 日志里怎么排查一个特定请求的完整链路？

**考察点**：可观测性认知。

项目中集成了 Micrometer Tracing + Brave（`micrometer-tracing-bridge-brave`），日志格式中包含 `[%X{traceId}]` 和 `[%X{spanId}]`。同一个请求的所有日志会有相同的 traceId，可以在日志文件中 grep。

---

## 七、代码实操

### 22. AgentService 里的 `callToolsIfNeeded()` 用了一堆正则匹配来判断用户意图，这种方式的局限性是什么？你会怎么改进？

**考察点**：代码设计和 AI 应用的经验。

局限性：规则覆盖不全（同一种意图有无数种表达方式）；维护困难（每加一种车型或问法都要改正则）；误判率高。

改进方案：用 LLM 做意图识别/工具选择（Function Calling）；或至少用 embedding + 分类器做语义级匹配。

### 23. `buildRagContext()` 每次提问都会调用 Embedding API，如果 QPS 很高，Embedding API 会成为瓶颈吗？如何优化？

**考察点**：性能优化思维。

会，每次提问需要调用一次 Embedding API 把用户问题转为向量。高 QPS 下：
- Embedding API 本身可能有限流
- 每次都调 API 有网络延迟

优化：对常见/重复的问题做缓存（Redis 缓存 question → embedding）；批量化处理；使用本地 embedding 模型。

### 24. 文档分块的 `smartChunkContent()` 方法里 split 用了 `\\n{2,}`，如果上传的文档是一个没有空行的纯长文本（如早期的 PDF 转 TXT），会发生什么？

**考察点**：边界条件思考。

整个文档会被当做一个段落，如果超过 500 字符，会进入 `splitIntoSentences()` 按标点切分，最终按 500 字符聚合成多个 chunk。如果没有标点符号（极端情况），`splitIntoSentences()` 降级为每 200 字符固定切分。所以不会出 bug，但因为缺少语义边界，chunk 质量会下降。

---

## 八、开放性问题

### 25. 如果让你重新设计这个系统，你会做哪些不同的技术决策？

期望候选人有反思能力，示例回答：
- 考虑用专门的向量数据库（Qdrant/Milvus）替代 pgvector 以获得更好的检索性能
- 文档分块引入语义分块（semantic chunking），而不是纯规则
- 引入 Reranker 提升检索精度
- 对话历史做窗口截断或摘要，避免超 context window
- 配置管理考虑 Nacos/Apollo 替代本地 yml

### 26. 如何评估这个 RAG 系统的回答质量？你有想过怎么做评测吗？

期望涉及：RAGAS 评测框架、忠实度（faithfulness）、答案相关性（answer relevancy）、上下文召回率（context recall）。可以构建测试集（问题 + 期望答案）做自动化评测。

---

## 九、Spring AI 深入

### 27. Spring AI 的 `ChatModel` 接口在你的项目里是怎么使用的？和直接调 DeepSeek HTTP API 相比有什么优势？

**考察点**：对 Spring AI 抽象层价值的理解。

项目中 `AgentService` 注入 `ChatModel`（由 Spring AI 的 OpenAI starter 自动配置），通过 `chatModel.call(new Prompt(messages))` 调用 LLM。优势：
- **可移植性**：换模型只改配置（base-url + api-key + model），不碰业务代码
- **统一抽象**：`ChatModel`、`EmbeddingModel` 接口屏蔽不同提供商的 API 差异
- **生态集成**：自动配置、健康检查、Observability 自动埋点
- **Tool 支持**：`@Tool` 注解让方法可以被 Agent 调用

但当前项目并没有完全利用 Spring AI 的 Tool 机制——`CarPriceCalculator` 的方法标注了 `@Tool`，但 `AgentService` 是手动调用它的，没有用 Spring AI 的 Function Calling 自动路由。

### 28. 你的 `CarPriceCalculator` 里两个方法标注了 `@Tool` 注解，但 AgentService 里是手动正则匹配后直接 new 调用的。Spring AI 的 Tool/Function Calling 机制应该怎么用？为什么你没用它的自动模式？

**考察点**：对 Function Calling 机制的理解和工程判断。

Spring AI 1.0.0 的 Tool 机制：在 `@Tool` 方法上声明 description 和参数注解，创建 `ChatClient` 时 `.tools(toolBean)` 注册，LLM 会自动判断是否需要调用工具并生成参数。

没用的原因（合理回答）：
- 正则匹配在这个场景下更快、更可控（不需要额外一次 LLM 调用来决定是否 call tool）
- 汽车销售场景的意图非常固定（算价格 / 算月供），规则匹配足够
- 节省 LLM token 消耗和延迟

追问：如果业务扩展到 50 种工具呢？→ 这时 Function Calling 的优势就体现出来了，规则维护成本爆炸。

### 29. 你的 Prompt 是怎么构建的？`SystemMessage` 和 `UserMessage` 分别承载什么内容？设计原则是什么？

**考察点**：Prompt Engineering 的实战能力。

项目中有两套 System Prompt：`DEFAULT_SYSTEM_PROMPT` 和 `CAR_SALES_SYSTEM_PROMPT`，通过 `selectSystemPrompt()` 按知识库类型选择。

System Prompt 承载：角色设定、能力范围声明、回答约束（只用文档内容、不编造、诚实告知）。
UserMessage 承载：检索到的文档内容 + 用户当前问题 +（有工具结果时）工具计算结果。

设计原则：System Prompt 定义"你是谁"和"边界"，UserMessage 提供"根据什么回答问题"。这是一种结构化 Prompt 模式。

### 30. 你的 System Prompt 是怎么选择 CAR_SALES 还是 DEFAULT 的？这个判断逻辑有什么问题？

**考察点**：设计细节的批判性思维。

通过检查知识库的 `config` JSON 里是否包含 `"docType":"CAR_MD"` 字符串来判断。

问题：用 `String.contains()` 做 JSON 语义判断非常脆弱——如果 config 格式变了（多了空格、换行、字段顺序变化），匹配就会失败。应该用 `ObjectMapper` 解析 JSON 后按字段判断。另外这个判断耦合在 `AgentService` 里，不如做成知识库的一个明确的 `type` 字段。

### 31. Spring AI 的自动配置帮你做了什么？如果让你手动接 DeepSeek 的 API，你需要处理哪些事情？

**考察点**：对 Spring Boot 自动配置和 HTTP 客户端集成的理解。

Spring AI 的 OpenAI starter 自动配置了：
- `OpenAiChatModel` bean（根据 `spring.ai.openai.*` 配置）
- HTTP 客户端（WebClient/RestClient）
- 请求/响应的序列化
- 流式输出支持
- 重试和错误处理

手动实现需要：构造 HTTP 请求、处理 OpenAI 兼容的 chat completion API 格式、管理 token 计数、处理流式响应（SSE）、错误处理和重试策略、模型参数调优等。

---

## 十、并发与异步处理

### 32. `@Async` 注解的底层原理是什么？Spring 是如何实现方法异步执行的？

**考察点**：对 Spring 异步机制的深入理解。

Spring 的 `@Async` 通过 AOP 代理实现。当调用标注了 `@Async` 的方法时，Spring 会拦截调用，将方法执行提交给 `TaskExecutor` 线程池，然后立即返回。核心组件：
- `AsyncAnnotationBeanPostProcessor` 在 Bean 初始化时创建代理
- `AsyncExecutionInterceptor` 负责拦截方法调用并提交到线程池
- `@EnableAsync` 激活整个机制

追问：同一个类里方法 A 直接调用方法 B（标注了 @Async），B 会异步执行吗？→ 不会！因为绕过了 AOP 代理，这是经典的 Spring AOP 自调用陷阱。

### 33. 你的 `documentProcessExecutor` 线程池参数（core=2, max=4, queue=100）是怎么定的？生产环境应该怎么配置？

**考察点**：线程池参数调优的经验。

这些参数应该根据实际负载和资源来确定：
- **corePoolSize**：取决于 CPU 核心数和任务类型。文档处理是 IO 密集型（读文件 + 调 API），可以设大一些（如 CPU 核心数 × 2）
- **maxPoolSize**：峰值容忍度。当前 4 可能偏保守
- **queueCapacity**：100 是缓冲队列。过大 → 任务堆积但迟迟不执行；过小 → 频繁触发拒绝策略

生产环境建议：先压测确定单机处理能力 → 根据 SLA 反推所需线程数 → 配置监控（线程池活跃线程数、队列大小、拒绝次数）→ 通过配置中心动态调整。

### 34. 上传文档的事务是 `@Transactional`，但文档处理是异步的。如果异步处理过程中抛异常了，上传事务已经提交了，数据会不一致吗？

**考察点**：事务边界和异步处理的交互理解。

会的，这正是当前设计的一个 tradeoff。`DocumentService.uploadDocument()` 的事务只涵盖：写文件到磁盘 + 插入 Document 记录。然后提交事务。异步的 `processDocument()` 在另一个线程的独立事务中运行。

如果异步处理失败：Document 记录已经是 UPLOADING/FAILED 状态，不会丢失。但如果成功将状态改为 INDEXED，中间环节（如 Embedding API 超时）失败时，状态已经被更新为 FAILED。

这里用的是**最终一致性**模型而非强一致性，通过状态机让用户看到处理进度。

追问问题：如果 insert Document 成功，但异步处理提交前服务器宕机了，文档状态会永远是 UPLOADING，你怎么处理？→ 定时任务扫描长时间处于 UPLOADING/PROCESSING 状态的文档，重新触发处理或标记为 FAILED。

### 35. 如果两个用户同时上传文档，它们的处理会互相影响吗？有没有并发安全问题？

**考察点**：对共享资源和并发安全的识别。

文档处理是独立的——每个文档有自己的文件路径、自己的 Document 记录、自己的 chunk 数据。不同文档之间没有共享可变状态，所以不会互相影响。

可能的并发问题：
- `./uploads/documents/` 文件系统操作：文件名用了 UUID，不会冲突
- 数据库操作：每条记录独立，MyBatis-Plus 的乐观锁 / 悲观锁在这个场景不需要
- 线程池：2 个核心线程被所有用户共享，高并发下新任务会排队

### 36. `ConcurrentHashMap` 和 `Hashtable` 的区别是什么？如果要实现一个缓存最近提问的 Embedding 结果的组件，你会用哪个？

**考察点**：并发集合的选择。

区别：`Hashtable` 对所有操作加 `synchronized`，串行化程度高，性能差。`ConcurrentHashMap` 使用分段锁（JDK7）或 CAS + synchronized（JDK8），并发度更高，读操作几乎无锁。

缓存 Embedding：`ConcurrentHashMap` 合适。但需要注意：
- 缓存淘汰策略（LRU），单纯 CHM 不会自动淘汰，可以用 `LinkedHashMap` 包装或引入 Caffeine
- 缓存 key 的设计（用 question 的 hash 还是内容本身）
- 内存上限（embedding 是 1536 维 float 数组，一个问题约 6KB，10 万条就是 600MB+）

---

## 十一、前后端协作

### 37. 你的 Controller 的 API 设计遵循了什么规范？为什么所有响应都用 `ApiResponse<T>` 包装？

**考察点**：API 设计的一致性和工程规范。

所有接口统一返回 `ApiResponse<T>`：`{ success: bool, message: string, data: T }`。好处：
- **前端解析统一**：不需要判断不同接口的返回格式
- **错误处理标准化**：业务异常和系统异常都通过 `GlobalExceptionHandler` 转为统一格式
- **可扩展**：未来可以在不破坏现有结构的情况下加字段（如 `traceId`、`timestamp`）

追问：success=true 但 data=null 和 success=false 有什么区别？→ success=true, data=null 表示操作成功但没有返回数据（如删除操作）；success=false 表示操作失败。

### 38. 文档上传是同步返回还是异步返回？前端怎么知道文档处理完了？

**考察点**：前后端异步交互的设计。

`POST /api/documents/upload` 是同步返回的——返回 Document 对象（状态=UPLOADING），HTTP 请求已结束。文档的实际处理在后台异步执行。

前端需要**轮询** `GET /api/documents/{id}` 查看状态变化：
- UPLOADING → PROCESSING → INDEXED（完成）
- 或 → FAILED（失败）

追问：还有哪些更好的方案？→ WebSocket 推送处理完成通知、Server-Sent Events (SSE) 推送进度、前端使用 `useSWR` / `react-query` 的 `refetchInterval` 自动轮询。

### 39. CORS 跨域配置里 `addAllowedOriginPattern("*")` 和 `setAllowCredentials(true)` 一起用有什么安全风险？

**考察点**：Web 安全基础。

这两个配置**不能同时生效**。CORS 规范规定：当 `credentials=true` 时，`Access-Control-Allow-Origin` 不能是 `*`（通配符），必须是具体的 origin。浏览器的 CORS 实现会拒绝这种响应。

当前配置用了 `addAllowedOriginPattern("*")`（Spring 提供的模式匹配，不是字面量 `*`），这绕过了上述限制，只要有匹配模式的具体 origin 都允许。安全风险：
- 任何网站都可以向你的 API 发起跨域请求并携带 cookie/认证信息
- 容易遭受 CSRF 攻击

生产环境应该配置为具体的前端域名列表。

### 40. 你的分页接口用的是 `page` + `pageSize` 参数，而不是 `offset` + `limit`，这两种方式各有什么优劣？

**考察点**：分页方案的设计权衡。

项目用的是基于页码的分页（`page=0&pageSize=10`），MyBatis-Plus 的 `Page` 内部转为 offset/limit。

**页码分页**：前端易用（上一页/下一页），传统页面展示习惯。但如果数据在两次请求间有插入/删除，"第 2 页"的数据可能和预期不一致（漂移问题）。

**游标分页（cursor-based）**：用上一页最后一条的 ID/时间戳作为下一页起点，数据一致性更好（不怕插入），适合无限滚动（feed 流）。但不支持随机跳页。

当前场景是管理后台（文档列表、问答历史），用页码分页是合适的。

### 41. Swagger/OpenAPI 文档是怎么生成的？`@Tag` 和 `@Operation` 注解的作用是什么？

**考察点**：API 文档化实践。

通过 `springdoc-openapi-starter-webmvc-ui`（SpringDoc）自动生成。在 Controller 类上加 `@Tag(name="文档管理")` 分组，在方法上加 `@Operation(summary="上传文档")` 描述接口。SpringDoc 会自动扫描 Spring MVC 的注解（`@RestController`、`@RequestMapping`、`@RequestParam` 等）生成 OpenAPI 3.0 规范的文档。

访问 `http://localhost:8080/swagger-ui.html` 可以看到 Swagger UI。访问 `/api-docs` 可以拿到 JSON 格式的 OpenAPI 规范。

追问：如果不想暴露某些接口怎么办？→ `@Hidden` 注解、`springdoc.paths-to-exclude` 配置、或在 SecurityConfig 中限制 Swagger 路径的访问。

### 42. 前端的问答界面在你提问后需要显示 LLM 的流式输出（逐字打印效果），你当前的后端能支持吗？如果不能，要怎么改？

**考察点**：对流式响应的认知。

当前后端的 `AgentService.answer()` 使用的是 `chatModel.call()` ——这是同步调用，返回完整结果。前端收到的是完整的 JSON 响应，无法做逐字效果。

要支持流式输出：
1. 使用 Spring AI 的流式 API：`chatModel.stream(new Prompt(messages))` 返回 `Flux<ChatResponse>`
2. Controller 返回 `MediaType.TEXT_EVENT_STREAM`（SSE），把每个 token 包装为 SSE 事件
3. 或使用 WebFlux 的 `ServerSentEvent` 封装
4. 前端用 `EventSource` 或 `fetch` + `ReadableStream` 消费事件流

同时需要处理：流式过程中出错怎么办、如何标记流结束、流式输出的 session 管理和限流。

---

## 十二、系统设计 & 场景题

### 43. 假设你的系统要支持 1000 个知识库，每个知识库平均 500 篇文档，pgvector 的检索会变慢吗？你会怎么优化？

**考察点**：规模化思考。

会变慢。50 万文档 × 假设每篇 10 个 chunk = 500 万个向量，在 1536 维下，全表扫描的余弦相似度计算会非常慢。

优化方案：
1. **pgvector 索引**：建立 IVFFlat 索引（需要先有数据再建索引）
2. **分区表**：按 `knowledge_base_id` 分区，查询时只扫描目标分区
3. **应用层过滤**：先查出知识库下的文档 ID 列表，检索时加 `document_id IN (...)` 条件
4. **缓存热门 KB 的检索结果**
5. **混合检索降级**：先用全文检索（PostgreSQL `tsvector`）粗筛，再对候选做向量精确匹配

### 44. 如果你的 LLM（DeepSeek）挂了，整个问答功能就不可用了。你会设计怎样的降级方案？

**考察点**：高可用和降级设计。

多级降级：
1. **主备模型切换**：配置第二个 LLM 提供商（如通义千问、GLM），DeepSeek 失败时自动切换
2. **纯检索模式**：LLM 不可用时，直接把检索到的文档片段返回给用户，不生成总结
3. **关键词匹配兜底**：最差情况直接用全文检索做关键词匹配，返回相关文档列表
4. **熔断器**：使用 Resilience4j 的 CircuitBreaker，连续失败 N 次后直接走降级逻辑，避免一直尝试调用，同时定期探测恢复

### 45. 现在你要给这个系统加一个"文档更新"功能：用户上传一个新版本的文档替换旧的，但正在有人基于旧文档问答。你怎么设计这个流程？

**考察点**：数据一致性和在线替换的工程方案。

关键挑战：更新过程中不中断服务、不出现检索到半新半旧 chunk 的情况。

方案：
1. **先插后删（推荐）**：新建 Document 记录 → 异步处理新文档 → 新文档 INDEXED 后 → 删除旧文档的 chunk 和记录。过渡期两个版本同时存在，检索可能混入新旧内容，但服务不中断。
2. **版本号方案**：同一个 `document_id` 加 `version` 字段，检索时只查最新版本，旧版本异步清理。
3. **双写后切换**：新 chunk 写入临时表 → 完成 → 事务中替换（rename 表 / 更新指针）。更复杂但一致性更好。

文档级事务隔离在 PostgreSQL 中难以做到真正的"瞬间切换"，需要根据业务容忍度选择。

### 46. 你的文本分块最大 500 字符，但如果用户的文档里的核心信息刚好跨了两个 chunk（比如一个表格的前半部分在一个 chunk，后半部分在下一个 chunk），检索时就可能漏掉。这个怎么解决？

**考察点**：对 RAG chunking 进阶策略的了解。

方案：
1. **重叠分块（Overlapping Chunks）**：每个 chunk 和相邻 chunk 有 50-100 字符的重叠区。最常用的方案。
2. **小 chunk + 大上下文（Small-to-Big Retrieval）**：用小 chunk 做检索（精度高），但检索命中后返回它所属的更大段落作为 LLM 上下文。
3. **父文档检索（Parent Document Retriever）**：类似上一条，chunk 只是索引，实际返回的是 chunk 所在的整个 section。
4. **多粒度索引**：同时索引 chunk 和 section 两级，检索时做结果合并去重。

### 47. 你这个项目中，如果让我给 Embedding 模型换成本地的（如 BGE-M3 部署在自有 GPU 上），应该改哪些地方？

**考察点**：对系统抽象层和实际替换的评估。

需要改的地方：
1. `DashScopeEmbeddingConfig`：移除或替换 DashScope 的 `EmbeddingModel` bean，用 BGE-M3 的实现（自建 HTTP 服务或用 ONNX/Transformers 本地加载）
2. Embedding 维度：BGE-M3 是 1024 维，项目中 `VECTOR_DIMENSION` 硬编码为 1536，pgvector 表也是 `vector(1536)`，需要改表结构或做维度映射
3. `application.yml`：去掉 `dashscope.*` 配置，改为自己的模型服务地址
4. 网络调用改为本地调用：延迟降低但需要 GPU 资源和模型管理

最大的坑：维度不一致导致表结构需要变更，存量数据需要重新向量化。

---

## 十三、代码质量 & 测试

### 48. 我看你的项目只有两个测试文件，覆盖率应该不高。如果让你给核心流程写测试，你会重点测哪些？怎么测？

**考察点**：测试策略和可测试性意识。

重点测试：
1. **文档分块逻辑**：`DocumentProcessService.smartChunkContent()` 和 `markdownSectionChunk()` ——纯逻辑，好测，各种边界输入（空文本、超长段落、无标点文本、frontmatter 异常）
2. **QA 问答流程**：Mock `ChatModel` 和 `EmbeddingModel`，验证检索结果能正确拼入 Prompt，验证 fallback 降级路径
3. **向量检索 SQL**：用真实 pgvector 验证三种距离算法的 SQL 正确性
4. **JWT 认证流程**：验证 token 生成 → 解析 → 过期校验的闭环

单元测试用 JUnit 5 + Mockito Mock 外部依赖。集成测试用 `@SpringBootTest` + Testcontainers 启动真实的 PostgreSQL/pgvector。

### 49. 你项目中 `callToolsIfNeeded()` 这个方法大概 30 多行，同时做了意图识别 + 参数提取 + 方法调用三件事。如果让你重构这个方法，你会怎么拆？

**考察点**：单一职责原则和代码重构能力。

理想拆分：
1. `ToolMatcher` 接口：`boolean matches(String question)` + `ToolResult execute(String question)`
2. 每种工具独立实现一个 `ToolMatcher`（如 `OnRoadPriceMatcher`、`MonthlyPaymentMatcher`）
3. `AgentService` 中注入 `List<ToolMatcher>`，遍历匹配并执行
4. 参数提取（`extractCarModel()`、`extractDownPaymentRatio()`）收归各个 ToolMatcher 内部

这样新增一种工具有清晰模式，不需要改 `AgentService`。和已有的 `AgentSkill` 接口是同一个设计模式。

### 50. 如果这个项目要加入 CI/CD 流水线，你认为需要哪些环节？

**考察点**：CI/CD 工程实践。

标准 Java 项目流水线：
1. **编译检查**：`mvn compile` 确保无编译错误
2. **代码风格**：Checkstyle / SpotBugs 静态分析
3. **单元测试**：`mvn test` + JaCoCo 覆盖率门槛（如 ≥ 60%）
4. **集成测试**：`mvn verify`，需要 CI 环境中有 PostgreSQL + pgvector（用 Testcontainers 或 Service Container）
5. **构建镜像**：`docker build`，推送到镜像仓库
6. **部署**：kubectl / docker-compose 部署到测试环境
7. **冒烟测试**：验证核心接口（auth/login、qa/question）

另外建议：分支保护（PR 必须通过 CI 才能 merge）、提交信息规范（Conventional Commits）。
