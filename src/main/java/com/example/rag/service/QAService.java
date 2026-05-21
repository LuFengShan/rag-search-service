package com.example.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.dto.request.QuestionRequest;
import com.example.rag.dto.response.AnswerResponse;
import com.example.rag.dto.response.PagedResponse;
import com.example.rag.entity.Answer;
import com.example.rag.entity.Question;
import com.example.rag.mapper.AnswerMapper;
import com.example.rag.mapper.QuestionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 问答服务 —— RAG 系统的问答核心入口
 * <p>
 * 负责串联用户的提问请求到最终的 AI 答案返回。
 * 不直接调用大模型，而是委托给 {@link AgentService} 完成检索增强生成（RAG）链路。
 *
 * <h3>一次问答的完整流程</h3>
 * <pre>
 * HTTP POST /api/qa/question
 *   └── QAController.askQuestion()
 *        └── QAService.askQuestion()  ← 本类
 *             ├── ① 将用户问题存入 question 表（状态 = PENDING）
 *             ├── ② 调用 AgentService.answer()
 *             │      ├── VectorService.embed(question)       → 阿里云 text-embedding-v4 向量化
 *             │      ├── VectorService.searchByCosine...()    → pgvector 余弦相似度检索 top-5
 *             │      ├── 构建 Prompt（System Prompt + 检索上下文 + 用户问题）
 *             │      ├── ChatModel.call(prompt)              → DeepSeek deepseek-chat 生成答案
 *             │      └── 返回 AnswerResponse（答案正文 + 引用来源 + 置信度）
 *             ├── ③ 将 Agent 返回的答案存入 answer 表
 *             ├── ④ 更新 question 状态 → ANSWERED
 *             └── ⑤ 返回 AnswerResponse 给前端
 * </pre>
 *
 * <h3>数据库设计（1:1 关系）</h3>
 * <pre>
 * question 表                    answer 表
 * ┌────┬──────────┬────────┐    ┌────┬─────────────┬────────┬─────────────────┬────────────┐
 * │ id │ question │ status │    │ id │ question_id │ answer │ sources (JSON)  │ confidence │
 * └────┴──────────┴────────┘    └────┴─────────────┴────────┴─────────────────┴────────────┘
 *                                        ↑ 外键关联 question.id
 * </pre>
 *
 * <h3>事务保证</h3>
 * askQuestion 方法标记了 {@code @Transactional}，
 * 确保 question 和 answer 两条记录要么同时成功入库，要么同时回滚，
 * 不会出现"问题存了但答案丢了"的不一致状态。
 *
 * @see AgentService RAG 检索增强生成核心
 * @see QuestionRequest 提问请求 DTO
 * @see AnswerResponse 答案响应 DTO（含答案正文、来源、置信度）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    private final QuestionMapper questionMapper;

    private final AnswerMapper answerMapper;

    /**
     * RAG Agent —— 负责 检索 + Prompt 拼装 + DeepSeek 调用
     * AgentService.answer() 内部包含了完整的 RAG 链路，
     * 本类只做：
     *   1. 调用 Agent 获取 AI 答案
     *   2. 持久化问答记录
     *   3. 组装 HTTP 响应
     */
    private final AgentService agentService;

    /**
     * Jackson ObjectMapper，用于将 AnswerResponse.SourceInfo 列表序列化为 JSON 字符串
     * 因为 PostgreSQL 不支持直接存 Java 对象列表，
     * 所以 answer 表的 sources 字段使用 TEXT 类型存 JSON
     */
    private final ObjectMapper objectMapper;

    /**
     * 处理用户提问 —— RAG 问答的完整入口
     * <p>
     * 这是一个 <b>同步</b> 方法。调用链：
     * {@code Controller → askQuestion() → AgentService.answer() → DeepSeek API}
     * <p>
     * 当前是同步模式（用户等待 AI 响应后立即返回结果）。
     * 如果后续需要流式输出（SSE / WebSocket），可以将 Agent 调用改为异步，
     * 参考 {@code DocumentProcessService} 的 {@code @Async} 实现方式。
     *
     * <h3>方法执行步骤</h3>
     * <ol>
     *   <li>构建 {@link Question} 实体，设置初始状态为 {@code PENDING}（"待回答"）</li>
     *   <li>将 Question 插入数据库（此时前端可以通过轮询看到状态）</li>
     *   <li>调用 {@link AgentService#answer} 获取 AI 生成的答案</li>
     *   <li>将答案内容和引用来源序列化为 {@link Answer} 实体存入数据库</li>
     *   <li>更新 Question 状态为 {@code ANSWERED}（"已回答"）</li>
     *   <li>返回 {@link AnswerResponse} 给 Controller 层</li>
     * </ol>
     *
     * @param request 包含问题文本和知识库 ID 的请求体
     *                <ul>
     *                  <li>{@code question}：用户输入的自然语言问题（必填，不能为空）</li>
     *                  <li>{@code knowledgeBaseId}：限定检索范围的知识库 ID（可选，传 null 则全局检索）</li>
     *                </ul>
     * @param userId  当前登录用户的 UUID，从 JWT Token 中提取
     * @return Agent 生成的答案响应，包含：
     *         <ul>
     *           <li>{@code answer}：DeepSeek 生成的答案文本</li>
     *           <li>{@code sources}：引用的文档片段列表（文档 ID、标题、内容摘要、置信度）</li>
     *           <li>{@code confidence}：整体置信度评分（0.0 ~ 1.0）</li>
     *         </ul>
     */
    @Transactional
    public AnswerResponse askQuestion(QuestionRequest request, UUID userId) {
        // ===== 步骤 1：创建问题记录（状态 = PENDING） =====
        // 使用 Builder 模式构建实体，MyBatis-Plus 的 ASSIGN_UUID 策略自动生成 UUID 主键
        Question question = Question.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .question(request.getQuestion())
                .knowledgeBaseId(request.getKnowledgeBaseId())
                .status(Question.Status.PENDING)
                .build();

        // MyBatis-Plus 自动填充 createdAt 字段（见 MyMetaObjectHandler）
        questionMapper.insert(question);

        // ===== 步骤 2：调用 RAG Agent 获取 AI 答案 =====
        // AgentService.answer() 内部链路：
        //   问题文本 → 向量化（阿里云 text-embedding-v4）
        //          → pgvector 余弦相似度检索 top-5
        //          → 构建 Prompt（System Prompt + 检索到的文档片段 + 用户问题）
        //          → DeepSeek Chat API 流式/非流式生成答案
        //          → 解析返回 AnswerResponse
        AnswerResponse answer = agentService.answer(request.getQuestion(), request.getKnowledgeBaseId());

        // ===== 步骤 3：创建答案记录 =====
        // answer 表通过 question_id 外键与 question 表关联（1:1）

        Answer answerEntity = Answer.builder()
                .id(UUID.randomUUID())
                .questionId(question.getId())
                .answer(answer.getAnswer())          // AI 生成的答案正文
                .confidence(answer.getConfidence())  // 置信度评分
                .build();

        // ===== 步骤 4：序列化引用来源为 JSON 字符串 =====
        // sources 字段在 MySQL/PostgreSQL 中定义为 TEXT 类型，存 JSON 字符串
        // 例：[{"documentId":"xxx","documentTitle":"知识库文档","chunkContent":"...","confidence":0.85}, ...]
        // 为什么不直接存 Java 对象？
        //   1. PostgreSQL 原生 JSONB 类型 MyBatis-Plus 支持有限
        //   2. TEXT + JSON 更通用，切换数据库不需要改 schema
        //   3. 查询时反序列化回来即可
        try {
            answerEntity.setSources(objectMapper.writeValueAsString(answer.getSources()));
        } catch (JsonProcessingException e) {
            // 序列化失败时存入空数组，不阻断业务流程
            answerEntity.setSources("[]");
        }

        answerMapper.insert(answerEntity);

        // ===== 步骤 5：更新问题状态 =====
        // PENDING → ANSWERED，前端轮询到此状态即可展示答案
        question.setStatus(Question.Status.ANSWERED);
        questionMapper.updateById(question);

        // ===== 步骤 6：返回答案给 Controller → 最终响应给用户 =====
        return answer;
    }

    /**
     * 分页查询用户的提问历史
     * <p>
     * 按时间倒序返回当前用户的所有提问记录。
     * 使用 MyBatis-Plus 分页插件，零 SQL 手写。
     *
     * <h3>分页参数说明</h3>
     * MyBatis-Plus 的 {@code Page} 对象页码从 <b>1</b> 开始，
     * 但前端通常习惯从 <b>0</b> 开始计数。
     * 所以这里做了 {@code page + 1} 的转换，对前端透明。
     *
     * <h3>返回结构</h3>
     * {@link PagedResponse} 是统一的分页响应封装：
     * <pre>
     * {
     *   "list": [ Question, Question, ... ],   ← 当前页数据
     *   "total": 42,                            ← 总记录数
     *   "page": 0,                              ← 当前页码（前端风格的 0-based）
     *   "pageSize": 10,                         ← 每页条数
     *   "totalPages": 5                         ← 总页数
     * }
     * </pre>
     *
     * @param userId   当前登录用户 ID，只返回该用户的提问记录
     * @param page     页码（从 0 开始）
     * @param pageSize 每页记录数
     * @return 分页封装的问题列表
     */
    public PagedResponse<Question> getQuestionHistory(UUID userId, int page, int pageSize) {
        // MyBatis-Plus 分页对象，页码从 1 开始（兼容后端习惯）
        Page<Question> pageParam = new Page<>(page + 1, pageSize);

        // questionMapper.findByUserId 是自定义查询方法，
        // 定义在 QuestionMapper 接口中，使用 @Select 注解或 XML Mapper
        IPage<Question> questionPage = questionMapper.findByUserId(pageParam, userId);

        // 将 MyBatis-Plus 的分页结果转换为项目统一的 PagedResponse 格式
        return PagedResponse.<Question>builder()
                .list(questionPage.getRecords())       // 当前页的实际数据
                .total(questionPage.getTotal())        // 符合条件的总记录数
                .page(page)                            // 返回前端风格的页码（0-based）
                .pageSize(pageSize)
                .totalPages((int) questionPage.getPages())  // 总页数，用于前端分页控件
                .build();
    }
}
