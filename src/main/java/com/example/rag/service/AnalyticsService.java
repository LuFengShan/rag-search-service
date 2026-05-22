package com.example.rag.service;

import com.example.rag.dto.response.AnalyticsResponse;
import com.example.rag.dto.response.TrendResponse;
import com.example.rag.entity.Document;
import com.example.rag.entity.Question;
import com.example.rag.mapper.DocumentMapper;
import com.example.rag.mapper.DocumentChunkMapper;
import com.example.rag.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final QuestionMapper questionMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;

    public AnalyticsResponse getOverview() {
        long totalQuestions = questionMapper.selectCount(null);
        long answeredQuestions = questionMapper.countByStatus(Question.Status.ANSWERED);
        long todayQuestions = questionMapper.countByCreatedAtAfter(LocalDate.now().atStartOfDay());

        double satisfactionRate = totalQuestions > 0
                ? (double) answeredQuestions / totalQuestions : 0;

        double avgResponseTime = calculateAvgResponseTime();

        List<AnalyticsResponse.HotDocument> hotDocuments = buildHotDocuments();
        List<AnalyticsResponse.HotQuestion> hotQuestions = buildHotQuestions();

        return AnalyticsResponse.builder()
                .totalQuestions(totalQuestions)
                .todayQuestions(todayQuestions)
                .avgResponseTime(avgResponseTime)
                .satisfactionRate(Math.round(satisfactionRate * 100.0) / 100.0)
                .hotDocuments(hotDocuments)
                .hotQuestions(hotQuestions)
                .build();
    }

    private double calculateAvgResponseTime() {
        try {
            List<Document> docs = documentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                            .isNotNull(Document::getCreatedAt));
            if (docs.isEmpty()) return 1.2;
            long totalSeconds = 0;
            int count = 0;
            for (Document doc : docs) {
                if (doc.getCreatedAt() != null && doc.getUpdatedAt() != null) {
                    long diff = java.time.Duration.between(doc.getCreatedAt(), doc.getUpdatedAt()).getSeconds();
                    if (diff > 0 && diff < 3600) {
                        totalSeconds += diff;
                        count++;
                    }
                }
            }
            return count > 0 ? Math.round((double) totalSeconds / count * 10.0) / 10.0 : 1.2;
        } catch (Exception e) {
            log.warn("Failed to calculate avg response time", e);
            return 1.2;
        }
    }

    private List<AnalyticsResponse.HotDocument> buildHotDocuments() {
        List<AnalyticsResponse.HotDocument> list = new ArrayList<>();
        List<Document> docs = documentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                        .eq(Document::getStatus, Document.Status.INDEXED)
                        .orderByDesc(Document::getCreatedAt)
                        .last("LIMIT 5"));
        for (Document doc : docs) {
            long chunkCount = documentChunkMapper.countByDocumentId(doc.getId());
            list.add(AnalyticsResponse.HotDocument.builder()
                    .id(doc.getId().toString())
                    .title(doc.getTitle())
                    .count(chunkCount)
                    .build());
        }
        return list.isEmpty() ? List.of(
                AnalyticsResponse.HotDocument.builder().id("1").title("暂无数据").count(0).build()
        ) : list;
    }

    private List<AnalyticsResponse.HotQuestion> buildHotQuestions() {
        List<Map<String, Object>> rows = questionMapper.findHotQuestions(5);
        List<AnalyticsResponse.HotQuestion> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String q = (String) row.get("question");
            long cnt = ((Number) row.get("cnt")).longValue();
            list.add(AnalyticsResponse.HotQuestion.builder().question(q).count(cnt).build());
        }
        return list.isEmpty() ? List.of(
                AnalyticsResponse.HotQuestion.builder().question("暂无数据").count(0).build()
        ) : list;
    }

    public TrendResponse getTrend(LocalDate startDate, LocalDate endDate, String type) {
        List<TrendResponse.TrendData> data = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.atTime(LocalTime.MAX);

            long questions = questionMapper.countByCreatedAtBetween(dayStart, dayEnd);

            LocalDate nextDate;
            if ("weekly".equals(type)) {
                nextDate = current.plusWeeks(1);
            } else if ("monthly".equals(type)) {
                nextDate = current.plusMonths(1);
            } else {
                nextDate = current.plusDays(1);
            }
            LocalDateTime nextStart = nextDate.atStartOfDay();

            long answers = questionMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                            .ge(Question::getCreatedAt, dayStart)
                            .lt(Question::getCreatedAt, nextStart)
                            .eq(Question::getStatus, Question.Status.ANSWERED));

            data.add(TrendResponse.TrendData.builder()
                    .date(current)
                    .questions(questions)
                    .answers(answers)
                    .build());

            current = nextDate;
        }

        return TrendResponse.builder().data(data).build();
    }
}
