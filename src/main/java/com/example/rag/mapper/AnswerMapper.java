package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.entity.Answer;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface AnswerMapper extends BaseMapper<Answer> {

    default Optional<Answer> findByQuestionId(UUID questionId) {
        Answer answer = selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Answer>()
                .eq(Answer::getQuestionId, questionId));
        return Optional.ofNullable(answer);
    }
}
