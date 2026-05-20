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

@Service
@RequiredArgsConstructor
public class KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Transactional
    public KnowledgeBaseResponse createKnowledgeBase(CreateKnowledgeBaseRequest request, UUID userId) {
        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .description(request.getDescription())
                .embeddingModel(request.getEmbeddingModel())
                .config(request.getConfig())
                .createdBy(userId)
                .build();

        knowledgeBaseMapper.insert(knowledgeBase);
        return KnowledgeBaseResponse.fromEntity(knowledgeBase);
    }

    public List<KnowledgeBaseResponse> getAllKnowledgeBases() {
        return knowledgeBaseMapper.selectList(null).stream()
                .map(KnowledgeBaseResponse::fromEntity)
                .toList();
    }

    public KnowledgeBaseResponse getKnowledgeBaseById(UUID id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库", "id", id.toString());
        }
        return KnowledgeBaseResponse.fromEntity(kb);
    }

    @Transactional
    public KnowledgeBaseResponse updateKnowledgeBase(UUID id, UpdateKnowledgeBaseRequest request) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库", "id", id.toString());
        }

        if (request.getName() != null) {
            kb.setName(request.getName());
        }
        if (request.getDescription() != null) {
            kb.setDescription(request.getDescription());
        }
        if (request.getEmbeddingModel() != null) {
            kb.setEmbeddingModel(request.getEmbeddingModel());
        }
        if (request.getConfig() != null) {
            kb.setConfig(request.getConfig());
        }

        knowledgeBaseMapper.updateById(kb);
        return KnowledgeBaseResponse.fromEntity(kb);
    }

    @Transactional
    public void deleteKnowledgeBase(UUID id) {
        if (knowledgeBaseMapper.selectById(id) == null) {
            throw new ResourceNotFoundException("知识库", "id", id.toString());
        }
        knowledgeBaseMapper.deleteById(id);
    }
}