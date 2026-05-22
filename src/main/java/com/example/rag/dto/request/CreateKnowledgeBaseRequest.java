package com.example.rag.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateKnowledgeBaseRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    private String embeddingModel;

    private String config;

    private String docType;
}
