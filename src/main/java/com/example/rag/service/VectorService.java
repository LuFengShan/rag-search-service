package com.example.rag.service;

import com.example.rag.entity.DocumentChunk;
import com.example.rag.mapper.DocumentChunkMapper;
import com.example.rag.mapper.DocumentChunkVectorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 向量服务层
 * <p>
 * 负责文档分块的向量化处理和语义检索，是RAG系统的核心组件。
 * 主要功能：
 * <ul>
 *   <li>将文本转换为向量（嵌入）</li>
 *   <li>保存文档分块及其向量到数据库</li>
 *   <li>基于向量相似度进行语义检索</li>
 * </ul>
 *
 * <h3>向量检索算法</h3>
 * <table>
 *   <tr><th>算法</th><th>运算符</th><th>说明</th></tr>
 *   <tr><td>余弦相似度</td><td>&lt;=&gt;</td><td>常用，衡量方向相似度</td></tr>
 *   <tr><td>L2距离</td><td>&lt;#&gt;</td><td>欧氏距离，衡量空间距离</td></tr>
 *   <tr><td>内积</td><td>&lt;@&gt;</td><td>向量点积，数值越大越相似</td></tr>
 * </table>
 *
 * <h3>降级策略</h3>
 * 当 Embedding API 不可用时，使用基于文本哈希的伪随机向量作为降级方案。
 *
 * @see com.example.rag.config.DashScopeEmbeddingConfig 阿里云向量模型配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    /** 文档分块数据访问层 */
    private final DocumentChunkMapper documentChunkMapper;

    /** 文档分块向量数据访问层 */
    private final DocumentChunkVectorMapper documentChunkVectorMapper;

    /** 嵌入模型（DashScope text-embedding-v4） */
    private final EmbeddingModel embeddingModel;

    /** 向量维度（与模型配置一致） */
    private static final int VECTOR_DIMENSION = 1536;

    /**
     * 保存文档分块及其向量
     * <p>
     * 将文档分块内容和对应的向量存储到数据库。
     *
     * @param documentId 文档ID
     * @param chunkIndex 分块索引
     * @param content 分块内容
     * @param embedding 向量数组
     */
    @Transactional
    public void saveDocumentChunk(UUID documentId, int chunkIndex, String content, float[] embedding) {
        // 将向量数组转换为字符串格式
        String vectorString = arrayToString(embedding);

        // 插入到向量表
        documentChunkVectorMapper.insertWithVector(
                UUID.randomUUID(),
                documentId,
                chunkIndex,
                content,
                vectorString
        );

        log.debug("Saved document chunk: documentId={}, chunkIndex={}", documentId, chunkIndex);
    }

    /**
     * 基于余弦相似度检索
     * <p>
     * 使用 PostgreSQL 的向量扩展进行语义检索，返回最相似的文档分块。
     *
     * @param queryEmbedding 查询向量
     * @param knowledgeBaseId 知识库ID（可选，为null时查询所有）
     * @param limit 返回数量限制
     * @return 相似文档分块列表
     */
    public List<DocumentChunk> searchByCosineSimilarity(float[] queryEmbedding, UUID knowledgeBaseId, int limit) {
        String vectorString = arrayToString(queryEmbedding);
        return documentChunkVectorMapper.findByCosineSimilarity(vectorString, null, knowledgeBaseId, limit);
    }

    /**
     * 基于L2距离检索
     *
     * @param queryEmbedding 查询向量
     * @param knowledgeBaseId 知识库ID（可选）
     * @param limit 返回数量限制
     * @return 相似文档分块列表
     */
    public List<DocumentChunk> searchByL2Distance(float[] queryEmbedding, UUID knowledgeBaseId, int limit) {
        String vectorString = arrayToString(queryEmbedding);
        return documentChunkVectorMapper.findNearestL2(vectorString, null, knowledgeBaseId, limit);
    }

    /**
     * 基于内积检索
     *
     * @param queryEmbedding 查询向量
     * @param knowledgeBaseId 知识库ID（可选）
     * @param limit 返回数量限制
     * @return 相似文档分块列表
     */
    public List<DocumentChunk> searchByInnerProduct(float[] queryEmbedding, UUID knowledgeBaseId, int limit) {
        String vectorString = arrayToString(queryEmbedding);
        return documentChunkVectorMapper.findByNegativeInnerProduct(vectorString, null, knowledgeBaseId, limit);
    }

    /**
     * 删除文档的所有分块
     * <p>
     * 删除指定文档关联的所有分块记录。
     *
     * @param documentId 文档ID
     */
    public void deleteByDocumentId(UUID documentId) {
        documentChunkMapper.deleteByDocumentId(documentId);
    }

    /**
     * 统计文档的分块数量
     *
     * @param documentId 文档ID
     * @return 分块数量
     */
    public long countByDocumentId(UUID documentId) {
        return documentChunkMapper.countByDocumentId(documentId);
    }

    /**
     * 将向量数组转换为字符串
     * <p>
     * 格式：[0.1, 0.2, 0.3, ...]
     *
     * @param array 向量数组
     * @return 字符串表示
     */
    String arrayToString(float[] array) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 生成文本的向量表示
     * <p>
     * 调用 Embedding 模型将文本转换为向量。
     * 如果 API 调用失败，最多重试3次。
     * 如果所有重试都失败，使用降级方案生成伪随机向量。
     *
     * @param text 待向量化的文本
     * @return 向量数组（1536维）
     */
    @Retryable(
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2),
            recover = "embedFallback"
    )
    public float[] embed(String text) {
        float[] result = embeddingModel.embed(text);
        log.debug("Generated embedding via Alibaba text-embedding-v4, dimension={}", result.length);
        return result;
    }

    /**
     * embed 方法的降级恢复方法
     * <p>
     * 当所有重试都失败时，使用基于文本哈希的伪随机向量作为降级方案。
     *
     * @param e 最后一次重试抛出的异常
     * @param text 待向量化的文本
     * @return 伪随机向量数组
     */
    @Recover
    public float[] embedFallback(Exception e, String text) {
        log.warn("Embedding API failed after retries, using fallback mock embedding: {}", e.getMessage());
        return generateFallbackEmbedding(text);
    }

    /**
     * 生成降级向量（Mock Embedding）
     * <p>
     * 基于文本哈希生成伪随机向量，用于 API 不可用时的降级处理。
     *
     * @param text 待向量化的文本
     * @return 伪随机向量数组
     */
    private float[] generateFallbackEmbedding(String text) {
        float[] embedding = new float[VECTOR_DIMENSION];
        int hash = text != null ? text.hashCode() : 0;
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            // 使用正弦函数生成伪随机值，范围 [0, 1]
            embedding[i] = (float) (Math.sin(i * hash * 0.01) * 0.5 + 0.5);
        }
        return embedding;
    }
}