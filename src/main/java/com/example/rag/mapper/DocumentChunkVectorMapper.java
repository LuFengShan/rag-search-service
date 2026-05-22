package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.entity.DocumentChunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 文档分块向量数据访问层
 * <p>
 * 专门用于处理向量检索相关的数据库操作，使用 PostgreSQL 的 PGVector 扩展。
 * 支持多种向量相似度算法：余弦相似度、L2距离、内积。
 * </p>
 *
 * <h3>向量检索算法说明</h3>
 * <table>
 *   <tr><th>方法</th><th>运算符</th><th>算法</th><th>说明</th></tr>
 *   <tr><td>findNearest</td><td>&lt;=&gt;</td><td>余弦相似度</td><td>常用，范围 [-1, 1]</td></tr>
 *   <tr><td>findNearestL2</td><td>&lt;#&gt;</td><td>L2距离</td><td>欧氏距离，值越小越相似</td></tr>
 *   <tr><td>findByCosineSimilarity</td><td>&lt;=&gt;</td><td>余弦相似度</td><td>同 findNearest</td></tr>
 *   <tr><td>findByNegativeInnerProduct</td><td>&lt;@&gt;</td><td>内积</td><td>值越大越相似</td></tr>
 * </table>
 *
 * @see com.example.rag.entity.DocumentChunk 文档分块实体
 * @see com.example.rag.service.VectorService 向量服务
 */
@Mapper
public interface DocumentChunkVectorMapper extends BaseMapper<DocumentChunk> {

    /**
     * 插入文档分块及其向量
     * <p>
     * 使用 PostgreSQL 的 CAST 函数将向量字符串转换为 vector 类型。
     *
     * @param id 分块ID
     * @param documentId 文档ID
     * @param chunkIndex 分块索引
     * @param content 分块内容
     * @param embedding 向量字符串（格式：[0.1, 0.2, ...]）
     */
    @Insert("INSERT INTO document_chunk (id, document_id, chunk_index, content, embedding, created_at) " +
            "VALUES (#{id}, #{documentId}, #{chunkIndex}, #{content}, CAST(#{embedding} AS vector), NOW())")
    void insertWithVector(@Param("id") UUID id,
                          @Param("documentId") UUID documentId,
                          @Param("chunkIndex") int chunkIndex,
                          @Param("content") String content,
                          @Param("embedding") String embedding);

    /**
     * 使用默认相似度算法检索相似文档分块
     * <p>
     * 默认使用余弦相似度（&lt;=&gt; 运算符）。
     *
     * @param queryVector 查询向量（字符串格式）
     * @param documentId 文档ID（可选，为null时查询所有）
     * @param knowledgeBaseId 知识库ID（可选）
     * @param limit 返回数量限制
     * @return 相似文档分块列表
     */
    List<DocumentChunk> findNearest(@Param("queryVector") String queryVector,
                                   @Param("documentId") UUID documentId,
                                   @Param("knowledgeBaseId") UUID knowledgeBaseId,
                                   @Param("limit") int limit);

    /**
     * 使用L2距离检索相似文档分块
     * <p>
     * L2距离越小表示越相似。
     *
     * @param queryVector 查询向量
     * @param documentId 文档ID（可选）
     * @param knowledgeBaseId 知识库ID（可选）
     * @param limit 返回数量限制
     * @return 相似文档分块列表
     */
    List<DocumentChunk> findNearestL2(@Param("queryVector") String queryVector,
                                      @Param("documentId") UUID documentId,
                                      @Param("knowledgeBaseId") UUID knowledgeBaseId,
                                      @Param("limit") int limit);

    /**
     * 使用余弦相似度检索相似文档分块
     * <p>
     * 余弦相似度范围 [-1, 1]，值越大表示越相似。
     *
     * @param queryVector 查询向量
     * @param documentId 文档ID（可选）
     * @param knowledgeBaseId 知识库ID（可选）
     * @param limit 返回数量限制
     * @return 相似文档分块列表
     */
    List<DocumentChunk> findByCosineSimilarity(@Param("queryVector") String queryVector,
                                               @Param("documentId") UUID documentId,
                                               @Param("knowledgeBaseId") UUID knowledgeBaseId,
                                               @Param("limit") int limit);

    /**
     * 使用内积检索相似文档分块
     * <p>
     * 内积值越大表示越相似。
     *
     * @param queryVector 查询向量
     * @param documentId 文档ID（可选）
     * @param knowledgeBaseId 知识库ID（可选）
     * @param limit 返回数量限制
     * @return 相似文档分块列表
     */
    List<DocumentChunk> findByNegativeInnerProduct(@Param("queryVector") String queryVector,
                                                    @Param("documentId") UUID documentId,
                                                    @Param("knowledgeBaseId") UUID knowledgeBaseId,
                                                    @Param("limit") int limit);

    /**
     * 根据文档ID按分块索引排序查询
     *
     * @param documentId 文档ID
     * @return 按索引排序的分块列表
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(@Param("documentId") UUID documentId);
}