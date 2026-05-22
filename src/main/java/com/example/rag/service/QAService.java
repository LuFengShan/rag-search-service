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

@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    private final QuestionMapper questionMapper;

    private final AnswerMapper answerMapper;

    private final AgentService agentService;

    private final ObjectMapper objectMapper;

    @Transactional
    public AnswerResponse askQuestion(QuestionRequest request, UUID userId) {
        UUID sessionId = request.getSessionId();
        if (sessionId == null) {
            sessionId = UUID.randomUUID();
        }

        Question question = Question.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .sessionId(sessionId)
                .question(request.getQuestion())
                .knowledgeBaseId(request.getKnowledgeBaseId())
                .status(Question.Status.PENDING)
                .build();

        questionMapper.insert(question);

        AnswerResponse answer = agentService.answer(
                request.getQuestion(), request.getKnowledgeBaseId(), request.getSessionId());

        Answer answerEntity = Answer.builder()
                .id(UUID.randomUUID())
                .questionId(question.getId())
                .answer(answer.getAnswer())
                .confidence(answer.getConfidence())
                .build();

        try {
            answerEntity.setSources(objectMapper.writeValueAsString(answer.getSources()));
        } catch (JsonProcessingException e) {
            answerEntity.setSources("[]");
        }

        answerMapper.insert(answerEntity);

        question.setStatus(Question.Status.ANSWERED);
        questionMapper.updateById(question);

        return answer;
    }

    public List<ConversationSession> getSessions(UUID userId) {
        List<Map<String, Object>> rows = questionMapper.findSessionsByUserId(userId);
        List<ConversationSession> sessions = new ArrayList<>();
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

    public List<ConversationMessage> getSessionMessages(UUID sessionId) {
        List<Question> questions = questionMapper.findBySessionIdOrderByCreatedAt(sessionId);
        List<ConversationMessage> messages = new ArrayList<>();
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

    public PagedResponse<Question> getQuestionHistory(UUID userId, int page, int pageSize) {
        Page<Question> pageParam = new Page<>(page + 1, pageSize);

        IPage<Question> questionPage = questionMapper.findByUserId(pageParam, userId);

        return PagedResponse.<Question>builder()
                .list(questionPage.getRecords())
                .total(questionPage.getTotal())
                .page(page)
                .pageSize(pageSize)
                .totalPages((int) questionPage.getPages())
                .build();
    }
}
