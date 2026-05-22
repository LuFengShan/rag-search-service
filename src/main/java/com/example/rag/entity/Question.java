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
@TableName("question")
public class Question {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    @TableField("session_id")
    private UUID sessionId;

    @TableField("question")
    private String question;

    @TableField("knowledge_base_id")
    private UUID knowledgeBaseId;

    @TableField("status")
    private Status status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public enum Status {
        PENDING,
        ANSWERED,
        FAILED
    }
}
