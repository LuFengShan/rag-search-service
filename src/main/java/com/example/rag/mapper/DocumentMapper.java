package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.entity.Document;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.UUID;

/**
 * 文档数据访问层
 * <p>
 * 继承自 MyBatis-Plus 的 BaseMapper，提供基础的 CRUD 操作。
 * 额外定义了文档相关的查询方法。
 * </p>
 *
 * <h3>表结构</h3>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>UUID</td><td>主键，文档唯一标识</td></tr>
 *   <tr><td>title</td><td>VARCHAR</td><td>文档标题</td></tr>
 *   <tr><td>file_path</td><td>VARCHAR</td><td>文件存储路径</td></tr>
 *   <tr><td>file_type</td><td>VARCHAR</td><td>文件类型（扩展名）</td></tr>
 *   <tr><td>file_size</td><td>INT</td><td>文件大小（字节）</td></tr>
 *   <tr><td>metadata</td><td>JSONB</td><td>文档元数据</td></tr>
 *   <tr><td>knowledge_base_id</td><td>UUID</td><td>所属知识库ID</td></tr>
 *   <tr><td>uploaded_by</td><td>UUID</td><td>上传者用户ID</td></tr>
 *   <tr><td>status</td><td>VARCHAR</td><td>状态：UPLOADING/PROCESSING/INDEXED/FAILED</td></tr>
 *   <tr><td>created_at</td><td>TIMESTAMP</td><td>创建时间</td></tr>
 *   <tr><td>updated_at</td><td>TIMESTAMP</td><td>更新时间</td></tr>
 * </table>
 *
 * @see com.example.rag.entity.Document 文档实体
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 根据知识库ID分页查询文档
     *
     * @param page 分页参数
     * @param knowledgeBaseId 知识库ID
     * @return 分页文档列表
     */
    default IPage<Document> findByKnowledgeBaseId(Page<Document> page, UUID knowledgeBaseId) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId));
    }

    /**
     * 根据标题模糊分页查询文档
     *
     * @param page 分页参数
     * @param title 标题关键词
     * @return 分页文档列表
     */
    default IPage<Document> findByTitleContaining(Page<Document> page, String title) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .like(Document::getTitle, title));
    }

    /**
     * 根据知识库ID和标题模糊分页查询文档
     *
     * @param page 分页参数
     * @param knowledgeBaseId 知识库ID
     * @param title 标题关键词
     * @return 分页文档列表
     */
    default IPage<Document> findByKnowledgeBaseIdAndTitleContaining(Page<Document> page, UUID knowledgeBaseId, String title) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId)
                .like(Document::getTitle, title));
    }

    /**
     * 根据上传者ID查询文档列表
     *
     * @param uploadedBy 上传者用户ID
     * @return 文档列表
     */
    default List<Document> findByUploadedBy(UUID uploadedBy) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getUploadedBy, uploadedBy));
    }

    /**
     * 统计知识库中的文档数量
     *
     * @param knowledgeBaseId 知识库ID
     * @return 文档数量
     */
    default long countByKnowledgeBaseId(UUID knowledgeBaseId) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, knowledgeBaseId));
    }
}