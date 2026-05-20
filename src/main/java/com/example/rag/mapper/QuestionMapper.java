package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.entity.Question;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    default IPage<Question> findByUserId(Page<Question> page, UUID userId) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getUserId, userId));
    }

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
}