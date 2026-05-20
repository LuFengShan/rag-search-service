package com.example.rag.service;

import com.example.rag.dto.response.AnalyticsResponse;
import com.example.rag.dto.response.TrendResponse;
import com.example.rag.entity.Question;
import com.example.rag.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final QuestionMapper questionMapper;

    public AnalyticsResponse getOverview() {
        long totalQuestions = questionMapper.selectCount(null);
        long answeredQuestions = questionMapper.countByStatus(Question.Status.ANSWERED);

        double avgResponseTime = 2.5;
        double satisfactionRate = 0.85;

        List<AnalyticsResponse.HotDocument> hotDocuments = new ArrayList<>();
        hotDocuments.add(AnalyticsResponse.HotDocument.builder()
                .id("doc-1")
                .title("员工手册 2024")
                .count(156)
                .build());
        hotDocuments.add(AnalyticsResponse.HotDocument.builder()
                .id("doc-2")
                .title("财务报销制度")
                .count(89)
                .build());
        hotDocuments.add(AnalyticsResponse.HotDocument.builder()
                .id("doc-3")
                .title("技术白皮书")
                .count(67)
                .build());

        List<AnalyticsResponse.HotQuestion> hotQuestions = new ArrayList<>();
        hotQuestions.add(AnalyticsResponse.HotQuestion.builder()
                .question("请假流程是什么？")
                .count(45)
                .build());
        hotQuestions.add(AnalyticsResponse.HotQuestion.builder()
                .question("如何报销费用？")
                .count(38)
                .build());
        hotQuestions.add(AnalyticsResponse.HotQuestion.builder()
                .question("加班工资怎么算？")
                .count(27)
                .build());

        return AnalyticsResponse.builder()
                .totalQuestions(totalQuestions)
                .avgResponseTime(avgResponseTime)
                .satisfactionRate(satisfactionRate)
                .hotDocuments(hotDocuments)
                .hotQuestions(hotQuestions)
                .build();
    }

    public TrendResponse getTrend(LocalDate startDate, LocalDate endDate, String type) {
        List<TrendResponse.TrendData> data = new ArrayList<>();

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            data.add(TrendResponse.TrendData.builder()
                    .date(currentDate)
                    .questions((long) (Math.random() * 50 + 20))
                    .answers((long) (Math.random() * 45 + 18))
                    .build());

            if ("daily".equals(type)) {
                currentDate = currentDate.plusDays(1);
            } else if ("weekly".equals(type)) {
                currentDate = currentDate.plusWeeks(1);
            } else {
                currentDate = currentDate.plusMonths(1);
            }
        }

        return TrendResponse.builder()
                .data(data)
                .build();
    }
}
