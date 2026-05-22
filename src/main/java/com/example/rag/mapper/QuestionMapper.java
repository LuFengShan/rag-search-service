package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    default IPage<Question> findByUserId(Page<Question> page, UUID userId) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getUserId, userId));
    }

    default IPage<Question> findBySessionId(Page<Question> page, UUID sessionId) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getSessionId, sessionId)
                .eq(Question::getStatus, Question.Status.ANSWERED)
                .orderByAsc(Question::getCreatedAt));
    }

    default List<Question> findBySessionIdOrderByCreatedAt(UUID sessionId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getSessionId, sessionId)
                .eq(Question::getStatus, Question.Status.ANSWERED)
                .orderByAsc(Question::getCreatedAt));
    }

    @Select("SELECT CAST(q.session_id AS VARCHAR) as session_id, " +
            "MIN(q.question) as title, " +
            "COUNT(*) as message_count, " +
            "MAX(q.created_at) as last_message_at " +
            "FROM question q " +
            "WHERE q.user_id = #{userId}::uuid AND q.session_id IS NOT NULL " +
            "GROUP BY q.session_id " +
            "ORDER BY MAX(q.created_at) DESC")
    List<java.util.Map<String, Object>> findSessionsByUserId(@Param("userId") UUID userId);

    default IPage<Question> findByStatus(Page<Question> page, Question.Status status) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, status));
    }

    default long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .between(Question::getCreatedAt, start, end));
    }

    default long countByStatus(Question.Status status) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, status));
    }

    default long countByCreatedAtAfter(LocalDateTime after) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .ge(Question::getCreatedAt, after));
    }

    @Select("SELECT q.question as question, COUNT(*) as cnt FROM question q " +
            "GROUP BY q.question ORDER BY COUNT(*) DESC LIMIT #{limit}")
    List<java.util.Map<String, Object>> findHotQuestions(@Param("limit") int limit);
}
