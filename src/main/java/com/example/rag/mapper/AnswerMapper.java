package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.entity.Answer;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;
import java.util.UUID;

/**
 * 答案数据访问层
 * <p>
 * 继承自 MyBatis-Plus 的 BaseMapper，提供基础的 CRUD 操作。
 * 额外定义了答案相关的查询方法。
 * </p>
 *
 * <h3>表结构</h3>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>UUID</td><td>主键，答案唯一标识</td></tr>
 *   <tr><td>question_id</td><td>UUID</td><td>关联问题ID</td></tr>
 *   <tr><td>answer</td><td>TEXT</td><td>答案内容</td></tr>
 *   <tr><td>confidence</td><td>FLOAT</td><td>置信度（0-1）</td></tr>
 *   <tr><td>sources</td><td>JSONB</td><td>来源文档信息</td></tr>
 *   <tr><td>created_at</td><td>TIMESTAMP</td><td>创建时间</td></tr>
 * </table>
 *
 * @see com.example.rag.entity.Answer 答案实体
 */
@Mapper
public interface AnswerMapper extends BaseMapper<Answer> {

    /**
     * 根据问题ID查询答案
     *
     * @param questionId 问题ID
     * @return 答案 Optional 对象
     */
    default Optional<Answer> findByQuestionId(UUID questionId) {
        Answer answer = selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Answer>()
                .eq(Answer::getQuestionId, questionId));
        return Optional.ofNullable(answer);
    }
}