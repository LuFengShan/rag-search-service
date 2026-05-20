package com.example.rag.dto.response;

import com.example.rag.entity.KnowledgeBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseResponse {

    private UUID id;

    private String name;

    private String description;

    private String embeddingModel;

    private String config;

    private UUID createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static KnowledgeBaseResponse fromEntity(KnowledgeBase kb) {
        return KnowledgeBaseResponse.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .embeddingModel(kb.getEmbeddingModel())
                .config(kb.getConfig())
                .createdBy(kb.getCreatedBy())
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .build();
    }
}