package com.example.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.dto.request.QuestionRequest;
import com.example.rag.dto.response.AnswerResponse;
import com.example.rag.dto.response.ConversationMessage;
import com.example.rag.dto.response.ConversationSession;
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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 问答服务层
 * <p>
 * 负责管理用户的提问和回答流程，包括：
 * <ul>
 *   <li>提交问题获取答案</li>
 *   <li>管理对话会话</li>
 *   <li>获取对话历史记录</li>
 * </ul>
 *
 * <h3>问答流程</h3>
 * <pre>
 * 用户提问 → QAService.askQuestion() → AgentService.answer()
 *                                         ↓
 *                                    向量检索 + LLM生成
 *                                         ↓
 *                                    保存问题和答案记录
 * </pre>
 *
 * <h3>会话管理</h3>
 * 支持多轮对话，通过 sessionId 关联同一对话的多条消息。
 * 如果请求中没有 sessionId，会自动生成新的会话ID。
 *
 * @see com.example.rag.service.AgentService 核心问答逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    /** 问题数据访问层 */
    private final QuestionMapper questionMapper;

    /** 答案数据访问层 */
    private final AnswerMapper answerMapper;

    /** 代理服务（核心RAG逻辑） */
    private final AgentService agentService;

    /** JSON序列化工具 */
    private final ObjectMapper objectMapper;

    /**
     * 提交问题获取答案
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>生成或使用会话ID</li>
     *   <li>保存问题记录到数据库（状态=PENDING）</li>
     *   <li>调用 AgentService 获取答案</li>
     *   <li>保存答案记录到数据库</li>
     *   <li>更新问题状态为 ANSWERED</li>
     * </ol>
     *
     * @param request 问题请求体，包含问题内容、知识库ID、会话ID
     * @param userId 用户ID
     * @return 答案响应对象
     */
    @Transactional
    public AnswerResponse askQuestion(QuestionRequest request, UUID userId) {
        // 使用请求中的会话ID，或生成新的会话ID
        UUID sessionId = request.getSessionId();
        if (sessionId == null) {
            sessionId = UUID.randomUUID();
        }

        // 构建问题实体
        Question question = Question.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .sessionId(sessionId)
                .question(request.getQuestion())
                .knowledgeBaseId(request.getKnowledgeBaseId())
                .status(Question.Status.PENDING)
                .build();

        // 保存问题记录
        questionMapper.insert(question);

        // 调用 AgentService 获取答案（核心RAG逻辑）
        AnswerResponse answer = agentService.answer(
                request.getQuestion(), request.getKnowledgeBaseId(), request.getSessionId());

        // 构建答案实体
        Answer answerEntity = Answer.builder()
                .id(UUID.randomUUID())
                .questionId(question.getId())
                .answer(answer.getAnswer())
                .confidence(answer.getConfidence())
                .build();

        // 序列化来源信息
        try {
            answerEntity.setSources(objectMapper.writeValueAsString(answer.getSources()));
        } catch (JsonProcessingException e) {
            answerEntity.setSources("[]");
        }

        // 保存答案记录
        answerMapper.insert(answerEntity);

        // 更新问题状态为已回答
        question.setStatus(Question.Status.ANSWERED);
        questionMapper.updateById(question);

        log.info("Question answered: session={}, question={}", sessionId, request.getQuestion());
        return answer;
    }

    /**
     * 获取用户的会话列表
     * <p>
     * 查询用户的所有对话会话，按最后消息时间排序。
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    public List<ConversationSession> getSessions(UUID userId) {
        // 查询用户的所有会话
        List<Map<String, Object>> rows = questionMapper.findSessionsByUserId(userId);
        List<ConversationSession> sessions = new ArrayList<>();

        // 转换为响应对象
        for (Map<String, Object> row : rows) {
            String sidStr = (String) row.get("session_id");
            UUID sid = UUID.fromString(sidStr);
            String title = (String) row.get("title");
            int count = ((Number) row.get("message_count")).intValue();
            Object lastAtObj = row.get("last_message_at");
            LocalDateTime lastAt = lastAtObj instanceof Timestamp
                    ? ((Timestamp) lastAtObj).toLocalDateTime()
                    : (LocalDateTime) lastAtObj;

            sessions.add(ConversationSession.builder()
                    .sessionId(sid)
                    .title(title)
                    .messageCount(count)
                    .lastMessageAt(lastAt)
                    .build());
        }
        return sessions;
    }

    /**
     * 获取会话的所有消息
     * <p>
     * 查询指定会话的所有问答消息，按创建时间排序。
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public List<ConversationMessage> getSessionMessages(UUID sessionId) {
        // 查询会话的所有问题
        List<Question> questions = questionMapper.findBySessionIdOrderByCreatedAt(sessionId);
        List<ConversationMessage> messages = new ArrayList<>();

        // 转换为响应对象
        for (Question q : questions) {
            Answer a = answerMapper.findByQuestionId(q.getId()).orElse(null);
            messages.add(ConversationMessage.builder()
                    .questionId(q.getId())
                    .question(q.getQuestion())
                    .answer(a != null ? a.getAnswer() : null)
                    .status(q.getStatus().name())
                    .createdAt(q.getCreatedAt() != null ? q.getCreatedAt().toString() : null)
                    .build());
        }
        return messages;
    }

    /**
     * 获取用户的提问历史（分页）
     * <p>
     * 查询用户的所有提问记录，按创建时间降序排列。
     *
     * @param userId 用户ID
     * @param page 页码（从0开始）
     * @param pageSize 每页大小
     * @return 分页问题列表
     */
    public PagedResponse<Question> getQuestionHistory(UUID userId, int page, int pageSize) {
        Page<Question> pageParam = new Page<>(page + 1, pageSize);

        // 分页查询用户的问题
        IPage<Question> questionPage = questionMapper.findByUserId(pageParam, userId);

        // 构建响应
        return PagedResponse.<Question>builder()
                .list(questionPage.getRecords())
                .total(questionPage.getTotal())
                .page(page)
                .pageSize(pageSize)
                .totalPages((int) questionPage.getPages())
                .build();
    }

    /**
     * 删除指定会话
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>查询该会话下的所有问题ID</li>
     *   <li>批量删除所有关联的答案</li>
     *   <li>删除该会话下的所有问题</li>
     * </ol>
     * 三个操作在同一事务中，保证数据一致性。
     *
     * @param sessionId 会话ID
     */
    @Transactional
    public void deleteSession(UUID sessionId) {
        List<UUID> questionIds = questionMapper.findQuestionIdsBySessionId(sessionId);
        if (!questionIds.isEmpty()) {
            answerMapper.deleteByQuestionIds(questionIds);
        }
        questionMapper.deleteBySessionId(sessionId);
        log.info("Session deleted: sessionId={}, questions removed={}", sessionId, questionIds.size());
    }
}