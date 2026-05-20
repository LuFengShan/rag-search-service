package com.example.rag.entity;

import com.baomidou.mybatisplus.annotation.*;
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
@TableName("documents")
public class Document {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    @TableField("title")
    private String title;

    @TableField("file_path")
    private String filePath;

    @TableField("file_type")
    private String fileType;

    @TableField("file_size")
    private Integer fileSize;

    @TableField("metadata")
    private String metadata;

    @TableField("knowledge_base_id")
    private UUID knowledgeBaseId;

    @TableField("uploaded_by")
    private UUID uploadedBy;

    @TableField("status")
    private Status status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public enum Status {
        UPLOADING,
        PROCESSING,
        INDEXED,
        FAILED
    }
}