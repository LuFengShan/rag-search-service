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

/**
 * 问题数据访问层
 * <p>
 * 继承自 MyBatis-Plus 的 BaseMapper，提供基础的 CRUD 操作。
 * 额外定义了问题相关的查询方法，支持会话管理和统计分析。
 * </p>
 *
 * <h3>表结构</h3>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>UUID</td><td>主键，问题唯一标识</td></tr>
 *   <tr><td>user_id</td><td>UUID</td><td>提问用户ID</td></tr>
 *   <tr><td>session_id</td><td>UUID</td><td>会话ID（用于多轮对话）</td></tr>
 *   <tr><td>question</td><td>TEXT</td><td>问题内容</td></tr>
 *   <tr><td>knowledge_base_id</td><td>UUID</td><td>关联知识库ID</td></tr>
 *   <tr><td>status</td><td>VARCHAR</td><td>状态：PENDING/ANSWERED/FAILED</td></tr>
 *   <tr><td>created_at</td><td>TIMESTAMP</td><td>创建时间</td></tr>
 * </table>
 *
 * @see com.example.rag.entity.Question 问题实体
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 根据用户ID分页查询问题
     *
     * @param page 分页参数
     * @param userId 用户ID
     * @return 分页问题列表
     */
    default IPage<Question> findByUserId(Page<Question> page, UUID userId) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getUserId, userId));
    }

    /**
     * 根据会话ID分页查询已回答的问题
     *
     * @param page 分页参数
     * @param sessionId 会话ID
     * @return 分页问题列表
     */
    default IPage<Question> findBySessionId(Page<Question> page, UUID sessionId) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getSessionId, sessionId)
                .eq(Question::getStatus, Question.Status.ANSWERED)
                .orderByAsc(Question::getCreatedAt));
    }

    /**
     * 根据会话ID查询已回答的问题（按创建时间排序）
     *
     * @param sessionId 会话ID
     * @return 问题列表
     */
    default List<Question> findBySessionIdOrderByCreatedAt(UUID sessionId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getSessionId, sessionId)
                .eq(Question::getStatus, Question.Status.ANSWERED)
                .orderByAsc(Question::getCreatedAt));
    }

    /**
     * 查询用户的会话列表
     * <p>
     * 按最后消息时间降序排列，返回会话ID、首个问题（作为标题）、消息数量。
     *
     * @param userId 用户ID
     * @return 会话信息列表（Map格式）
     */
    @Select("SELECT CAST(q.session_id AS VARCHAR) as session_id, " +
            "MIN(q.question) as title, " +
            "COUNT(*) as message_count, " +
            "MAX(q.created_at) as last_message_at " +
            "FROM question q " +
            "WHERE q.user_id = #{userId}::uuid AND q.session_id IS NOT NULL " +
            "GROUP BY q.session_id " +
            "ORDER BY MAX(q.created_at) DESC")
    List<java.util.Map<String, Object>> findSessionsByUserId(@Param("userId") UUID userId);

    /**
     * 根据状态分页查询问题
     *
     * @param page 分页参数
     * @param status 问题状态
     * @return 分页问题列表
     */
    default IPage<Question> findByStatus(Page<Question> page, Question.Status status) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, status));
    }

    /**
     * 统计指定时间范围内的问题数量
     *
     * @param start 开始时间
     * @param end 结束时间
     * @return 问题数量
     */
    default long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .between(Question::getCreatedAt, start, end));
    }

    /**
     * 统计指定状态的问题数量
     *
     * @param status 问题状态
     * @return 问题数量
     */
    default long countByStatus(Question.Status status) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getStatus, status));
    }

    /**
     * 统计指定时间之后创建的问题数量
     *
     * @param after 时间点
     * @return 问题数量
     */
    default long countByCreatedAtAfter(LocalDateTime after) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .ge(Question::getCreatedAt, after));
    }

    /**
     * 查询热门问题
     * <p>
     * 按问题出现次数降序排列，用于运营分析。
     *
     * @param limit 返回数量限制
     * @return 热门问题列表（Map格式，包含问题内容和出现次数）
     */
    @Select("SELECT q.question as question, COUNT(*) as cnt FROM question q " +
            "GROUP BY q.question ORDER BY COUNT(*) DESC LIMIT #{limit}")
    List<java.util.Map<String, Object>> findHotQuestions(@Param("limit") int limit);
}