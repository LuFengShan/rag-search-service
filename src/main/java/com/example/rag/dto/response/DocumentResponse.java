package com.example.rag.dto.response;

import com.example.rag.entity.Document;
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
public class DocumentResponse {

    private UUID id;

    private String title;

    private String filePath;

    private String fileType;

    private Integer fileSize;

    private String metadata;

    private UUID knowledgeBaseId;

    private UUID uploadedBy;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer chunkCount;

    public static DocumentResponse fromEntity(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .filePath(document.getFilePath())
                .fileType(document.getFileType())
                .fileSize(document.getFileSize())
                .metadata(document.getMetadata())
                .knowledgeBaseId(document.getKnowledgeBaseId())
                .uploadedBy(document.getUploadedBy())
                .status(document.getStatus().name())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}