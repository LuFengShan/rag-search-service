package com.example.rag.service;

import com.example.rag.dto.request.CreateKnowledgeBaseRequest;
import com.example.rag.dto.request.UpdateKnowledgeBaseRequest;
import com.example.rag.dto.response.KnowledgeBaseResponse;
import com.example.rag.entity.KnowledgeBase;
import com.example.rag.exception.ResourceNotFoundException;
import com.example.rag.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 知识库服务层
 * <p>
 * 负责知识库的创建、查询、更新和删除等核心业务逻辑。
 * 知识库是文档的组织单元，每个知识库可以配置不同的嵌入模型和参数。
 * </p>
 *
 * <h3>知识库配置说明</h3>
 * <table>
 *   <tr><th>配置项</th><th>说明</th><th>示例</th></tr>
 *   <tr><td>docType</td><td>文档类型标识</td><td>CAR_MD（汽车文档）</td></tr>
 *   <tr><td>embeddingModel</td><td>嵌入模型名称</td><td>text-embedding-v4</td></tr>
 * </table>
 *
 * @see com.example.rag.entity.KnowledgeBase 知识库实体
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    /** 知识库数据访问层 */
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /**
     * 创建知识库
     * <p>
     * 根据请求参数创建新的知识库，支持自动配置默认配置项。
     *
     * @param request 创建请求体，包含名称、描述、嵌入模型等
     * @param userId 创建者用户ID
     * @return 知识库响应对象
     */
    @Transactional
    public KnowledgeBaseResponse createKnowledgeBase(CreateKnowledgeBaseRequest request, UUID userId) {
        // 处理配置项
        String config = request.getConfig();
        if (config == null || config.isEmpty()) {
            // 根据文档类型设置默认配置
            if ("CAR_MD".equals(request.getDocType())) {
                config = "{\"docType\":\"CAR_MD\"}";
            } else {
                config = "{}";
            }
        }

        // 构建知识库实体
        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .description(request.getDescription())
                .embeddingModel(request.getEmbeddingModel())
                .config(config)
                .createdBy(userId)
                .build();

        // 插入数据库
        knowledgeBaseMapper.insert(knowledgeBase);
        return KnowledgeBaseResponse.fromEntity(knowledgeBase);
    }

    /**
     * 获取所有知识库列表
     *
     * @return 知识库响应列表
     */
    public List<KnowledgeBaseResponse> getAllKnowledgeBases() {
        return knowledgeBaseMapper.selectList(null).stream()
                .map(KnowledgeBaseResponse::fromEntity)
                .toList();
    }

    /**
     * 根据ID获取知识库
     *
     * @param id 知识库UUID
     * @return 知识库响应对象
     * @throws ResourceNotFoundException 当知识库不存在时抛出
     */
    public KnowledgeBaseResponse getKnowledgeBaseById(UUID id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库", "id", id.toString());
        }
        return KnowledgeBaseResponse.fromEntity(kb);
    }

    /**
     * 更新知识库信息
     * <p>
     * 支持更新：名称、描述、嵌入模型、配置
     *
     * @param id 知识库UUID
     * @param request 更新请求体
     * @return 更新后的知识库响应对象
     * @throws ResourceNotFoundException 当知识库不存在时抛出
     */
    @Transactional
    public KnowledgeBaseResponse updateKnowledgeBase(UUID id, UpdateKnowledgeBaseRequest request) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库", "id", id.toString());
        }

        // 更新名称
        if (request.getName() != null) {
            kb.setName(request.getName());
        }
        // 更新描述
        if (request.getDescription() != null) {
            kb.setDescription(request.getDescription());
        }
        // 更新嵌入模型
        if (request.getEmbeddingModel() != null) {
            kb.setEmbeddingModel(request.getEmbeddingModel());
        }
        // 更新配置
        if (request.getConfig() != null) {
            kb.setConfig(request.getConfig());
        }

        // 保存更新
        knowledgeBaseMapper.updateById(kb);
        return KnowledgeBaseResponse.fromEntity(kb);
    }

    /**
     * 删除知识库
     * <p>
     * 删除知识库时需确保没有关联的文档，否则会触发外键约束异常。
     *
     * @param id 知识库UUID
     * @throws ResourceNotFoundException 当知识库不存在时抛出
     */
    @Transactional
    public void deleteKnowledgeBase(UUID id) {
        if (knowledgeBaseMapper.selectById(id) == null) {
            throw new ResourceNotFoundException("知识库", "id", id.toString());
        }
        knowledgeBaseMapper.deleteById(id);
    }
}