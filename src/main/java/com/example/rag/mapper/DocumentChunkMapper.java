package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.entity.DocumentChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.UUID;

/**
 * 文档分块数据访问层
 * <p>
 * 继承自 MyBatis-Plus 的 BaseMapper，提供基础的 CRUD 操作。
 * 额外定义了文档分块相关的查询和删除方法。
 * </p>
 *
 * <h3>表结构</h3>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>UUID</td><td>主键，分块唯一标识</td></tr>
 *   <tr><td>document_id</td><td>UUID</td><td>所属文档ID</td></tr>
 *   <tr><td>chunk_index</td><td>INT</td><td>分块索引（从0开始）</td></tr>
 *   <tr><td>content</td><td>TEXT</td><td>分块内容</td></tr>
 *   <tr><td>embedding</td><td>VECTOR(1536)</td><td>向量表示（PGVector扩展）</td></tr>
 *   <tr><td>created_at</td><td>TIMESTAMP</td><td>创建时间</td></tr>
 * </table>
 *
 * <h3>索引设计</h3>
 * <ul>
 *   <li>idx_document_chunk_document_id: document_id 字段索引</li>
 *   <li>使用 PGVector 的 ivfflat 索引加速向量检索</li>
 * </ul>
 *
 * @see com.example.rag.entity.DocumentChunk 文档分块实体
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /**
     * 根据文档ID查询分块列表
     *
     * @param documentId 文档ID
     * @return 分块列表
     */
    default List<DocumentChunk> findByDocumentId(UUID documentId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId));
    }

    /**
     * 统计文档的分块数量
     *
     * @param documentId 文档ID
     * @return 分块数量
     */
    default long countByDocumentId(UUID documentId) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, documentId));
    }

    /**
     * 根据文档ID删除所有分块
     * <p>
     * 删除文档时需要级联删除其所有分块记录。
     *
     * @param documentId 文档ID
     */
    @Delete("DELETE FROM document_chunk WHERE document_id = #{documentId}")
    void deleteByDocumentId(UUID documentId);
}