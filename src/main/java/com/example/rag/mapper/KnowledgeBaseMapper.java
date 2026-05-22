package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.UUID;

/**
 * 知识库数据访问层
 * <p>
 * 继承自 MyBatis-Plus 的 BaseMapper，提供基础的 CRUD 操作。
 * 额外定义了知识库相关的查询方法。
 * </p>
 *
 * <h3>表结构</h3>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>UUID</td><td>主键，知识库唯一标识</td></tr>
 *   <tr><td>name</td><td>VARCHAR</td><td>知识库名称</td></tr>
 *   <tr><td>description</td><td>VARCHAR</td><td>知识库描述</td></tr>
 *   <tr><td>embedding_model</td><td>VARCHAR</td><td>嵌入模型名称</td></tr>
 *   <tr><td>config</td><td>JSONB</td><td>配置信息（如 docType）</td></tr>
 *   <tr><td>created_by</td><td>UUID</td><td>创建者用户ID</td></tr>
 *   <tr><td>created_at</td><td>TIMESTAMP</td><td>创建时间</td></tr>
 *   <tr><td>updated_at</td><td>TIMESTAMP</td><td>更新时间</td></tr>
 * </table>
 *
 * @see com.example.rag.entity.KnowledgeBase 知识库实体
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    /**
     * 根据创建者ID查询知识库列表
     *
     * @param createdBy 创建者用户ID
     * @return 知识库列表
     */
    default List<KnowledgeBase> findByCreatedBy(UUID createdBy) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getCreatedBy, createdBy));
    }

    /**
     * 根据名称模糊查询知识库
     *
     * @param name 知识库名称关键词
     * @return 匹配的知识库列表
     */
    default List<KnowledgeBase> findByNameContaining(String name) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase>()
                .like(KnowledgeBase::getName, name));
    }
}