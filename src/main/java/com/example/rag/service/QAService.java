package com.example.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.dto.request.QuestionRequest;
import com.example.rag.dto.response.AnswerResponse;
import com.example.rag.dto.response.PagedResponse;
import com.example.rag.entity.Answer;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.entity.Question;
import com.example.rag.mapper.AnswerMapper;
import com.example.rag.mapper.QuestionMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final VectorService vectorService;
    private final ObjectMapper objectMapper;

    @Transactional
    public AnswerResponse askQuestion(QuestionRequest request, UUID userId) {
        Question question = Question.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .question(request.getQuestion())
                .knowledgeBaseId(request.getKnowledgeBaseId())
                .status(Question.Status.PENDING)
                .build();

        questionMapper.insert(question);

        AnswerResponse answer = generateAnswer(request.getQuestion(), request.getKnowledgeBaseId());

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

    private AnswerResponse generateAnswer(String questionText, UUID knowledgeBaseId) {
        float[] queryEmbedding = vectorService.generateMockEmbedding(questionText);

        List<DocumentChunk> similarChunks = vectorService.searchByCosineSimilarity(
                queryEmbedding, knowledgeBaseId, 5);

        List<AnswerResponse.SourceInfo> sources = new ArrayList<>();

        if (!similarChunks.isEmpty()) {
            for (DocumentChunk chunk : similarChunks) {
                sources.add(AnswerResponse.SourceInfo.builder()
                        .documentId(chunk.getDocumentId())
                        .documentTitle("知识库文档")
                        .chunkContent(chunk.getContent().length() > 200 ?
                                chunk.getContent().substring(0, 200) + "..." : chunk.getContent())
                        .confidence(0.85f + (float) Math.random() * 0.1f)
                        .build());
            }
        }

        if (questionText.contains("请假") || questionText.contains("假期")) {
            return AnswerResponse.builder()
                    .answer("员工请假需要提前填写请假申请表，经直属主管审批后生效。" +
                            "请假天数在3天以内的由部门经理审批，超过3天的需要总经理审批。")
                    .sources(sources)
                    .confidence(0.92f)
                    .build();
        }

        if (questionText.contains("报销") || questionText.contains("费用")) {
            return AnswerResponse.builder()
                    .answer("报销流程：员工需提供发票及相关凭证，填写报销单后提交部门审核，" +
                            "审核通过后由财务部门统一处理。")
                    .sources(sources)
                    .confidence(0.90f)
                    .build();
        }

        if (questionText.contains("加班") || questionText.contains("加班费")) {
            return AnswerResponse.builder()
                    .answer("工作日加班按1.5倍工资计算，周末加班按2倍工资计算，" +
                            "法定节假日加班按3倍工资计算。")
                    .sources(sources)
                    .confidence(0.88f)
                    .build();
        }

        return AnswerResponse.builder()
                .answer("根据知识库内容，您的问题涉及以下方面：" + questionText +
                        "。建议您查看相关文档获取详细信息。")
                .sources(sources)
                .confidence(0.75f + (float) Math.random() * 0.1f)
                .build();
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