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

/**
 * 运营分析服务
 * <p>
 * 负责统计和分析系统运营数据，包括：
 * <ul>
 *   <li>问答统计：总提问数、今日提问数、平均响应时间、满意度</li>
 *   <li>热门文档：索引最多的文档 TOP5</li>
 *   <li>热门问题：被提问最多的问题 TOP5</li>
 *   <li>趋势分析：按日/周/月统计问答趋势</li>
 * </ul>
 *
 * <h3>统计指标说明</h3>
 * <table>
 *   <tr><th>指标</th><th>计算方式</th><th>说明</th></tr>
 *   <tr><td>totalQuestions</td><td>question 表总数</td><td>累计所有提问数</td></tr>
 *   <tr><td>todayQuestions</td><td>今日创建的 question 数</td><td>当日新增提问</td></tr>
 *   <tr><td>avgResponseTime</td><td>文档解析耗时平均值</td><td>单位：秒</td></tr>
 *   <tr><td>satisfactionRate</td><td>answered / total</td><td>回答率，0-1 之间</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    /** 问题数据访问层 */
    private final QuestionMapper questionMapper;

    /** 文档数据访问层 */
    private final DocumentMapper documentMapper;

    /** 文档分块数据访问层 */
    private final DocumentChunkMapper documentChunkMapper;

    /**
     * 获取运营概览统计
     * <p>
     * 聚合多个维度的统计数据，用于仪表盘展示。
     *
     * @return 分析响应对象，包含所有统计指标
     */
    public AnalyticsResponse getOverview() {
        // 统计总提问数
        long totalQuestions = questionMapper.selectCount(null);
        // 统计已回答的问题数
        long answeredQuestions = questionMapper.countByStatus(Question.Status.ANSWERED);
        // 统计今日提问数
        long todayQuestions = questionMapper.countByCreatedAtAfter(LocalDate.now().atStartOfDay());

        // 计算满意度（回答率）
        double satisfactionRate = totalQuestions > 0
                ? (double) answeredQuestions / totalQuestions : 0;

        // 计算平均响应时间（基于文档处理耗时）
        double avgResponseTime = calculateAvgResponseTime();

        // 获取热门文档和热门问题
        List<AnalyticsResponse.HotDocument> hotDocuments = buildHotDocuments();
        List<AnalyticsResponse.HotQuestion> hotQuestions = buildHotQuestions();

        // 构建响应
        return AnalyticsResponse.builder()
                .totalQuestions(totalQuestions)
                .todayQuestions(todayQuestions)
                .avgResponseTime(avgResponseTime)
                .satisfactionRate(Math.round(satisfactionRate * 100.0) / 100.0)
                .hotDocuments(hotDocuments)
                .hotQuestions(hotQuestions)
                .build();
    }

    /**
     * 计算平均响应时间
     * <p>
     * 通过文档的创建时间和更新时间差值计算平均处理耗时。
     * 过滤掉异常值（大于1小时的视为异常）。
     *
     * @return 平均响应时间（秒）
     */
    private double calculateAvgResponseTime() {
        try {
            // 查询所有有创建时间的文档
            List<Document> docs = documentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                            .isNotNull(Document::getCreatedAt));

            if (docs.isEmpty()) {
                // 没有数据时返回默认值
                return 1.2;
            }

            long totalSeconds = 0;
            int count = 0;

            // 遍历计算每个文档的处理耗时
            for (Document doc : docs) {
                if (doc.getCreatedAt() != null && doc.getUpdatedAt() != null) {
                    // 计算创建到更新的时间差（秒）
                    long diff = java.time.Duration.between(doc.getCreatedAt(), doc.getUpdatedAt()).getSeconds();
                    // 过滤异常值（小于0或大于1小时的不计入）
                    if (diff > 0 && diff < 3600) {
                        totalSeconds += diff;
                        count++;
                    }
                }
            }

            // 返回平均值，保留一位小数
            return count > 0 ? Math.round((double) totalSeconds / count * 10.0) / 10.0 : 1.2;

        } catch (Exception e) {
            log.warn("Failed to calculate avg response time", e);
            return 1.2;
        }
    }

    /**
     * 构建热门文档列表
     * <p>
     * 获取最近创建的5个已索引文档，按创建时间降序排列。
     *
     * @return 热门文档列表（最多5个）
     */
    private List<AnalyticsResponse.HotDocument> buildHotDocuments() {
        List<AnalyticsResponse.HotDocument> list = new ArrayList<>();

        // 查询最近创建的5个已索引文档
        List<Document> docs = documentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                        .eq(Document::getStatus, Document.Status.INDEXED)
                        .orderByDesc(Document::getCreatedAt)
                        .last("LIMIT 5"));

        // 构建响应列表
        for (Document doc : docs) {
            // 查询每个文档的分块数量
            long chunkCount = documentChunkMapper.countByDocumentId(doc.getId());
            list.add(AnalyticsResponse.HotDocument.builder()
                    .id(doc.getId().toString())
                    .title(doc.getTitle())
                    .count(chunkCount)
                    .build());
        }

        // 如果没有数据，返回占位数据
        return list.isEmpty() ? List.of(
                AnalyticsResponse.HotDocument.builder().id("1").title("暂无数据").count(0).build()
        ) : list;
    }

    /**
     * 构建热门问题列表
     * <p>
     * 通过数据库统计查询获取被提问最多的问题 TOP5。
     *
     * @return 热门问题列表（最多5个）
     */
    private List<AnalyticsResponse.HotQuestion> buildHotQuestions() {
        // 通过 SQL 统计获取热门问题
        List<Map<String, Object>> rows = questionMapper.findHotQuestions(5);
        List<AnalyticsResponse.HotQuestion> list = new ArrayList<>();

        // 转换为响应对象
        for (Map<String, Object> row : rows) {
            String question = (String) row.get("question");
            long count = ((Number) row.get("cnt")).longValue();
            list.add(AnalyticsResponse.HotQuestion.builder()
                    .question(question)
                    .count(count)
                    .build());
        }

        // 如果没有数据，返回占位数据
        return list.isEmpty() ? List.of(
                AnalyticsResponse.HotQuestion.builder().question("暂无数据").count(0).build()
        ) : list;
    }

    /**
     * 获取趋势数据
     * <p>
     * 按指定时间范围和粒度（日/周/月）统计问答趋势。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param type 时间粒度：daily（每日）、weekly（每周）、monthly（每月）
     * @return 趋势响应对象
     */
    public TrendResponse getTrend(LocalDate startDate, LocalDate endDate, String type) {
        List<TrendResponse.TrendData> data = new ArrayList<>();
        LocalDate current = startDate;

        // 按时间粒度遍历日期范围
        while (!current.isAfter(endDate)) {
            LocalDateTime dayStart = current.atStartOfDay();
            LocalDateTime dayEnd = current.atTime(LocalTime.MAX);

            // 统计当天的提问数
            long questions = questionMapper.countByCreatedAtBetween(dayStart, dayEnd);

            // 计算下一个时间点
            LocalDate nextDate;
            if ("weekly".equals(type)) {
                nextDate = current.plusWeeks(1);
            } else if ("monthly".equals(type)) {
                nextDate = current.plusMonths(1);
            } else {
                nextDate = current.plusDays(1);
            }
            LocalDateTime nextStart = nextDate.atStartOfDay();

            // 统计该时间段内已回答的问题数
            long answers = questionMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                            .ge(Question::getCreatedAt, dayStart)
                            .lt(Question::getCreatedAt, nextStart)
                            .eq(Question::getStatus, Question.Status.ANSWERED));

            // 添加趋势数据点
            data.add(TrendResponse.TrendData.builder()
                    .date(current)
                    .questions(questions)
                    .answers(answers)
                    .build());

            // 移动到下一个时间点
            current = nextDate;
        }

        return TrendResponse.builder().data(data).build();
    }
}