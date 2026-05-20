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
@TableName("answer")
public class Answer {

    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    @TableField("question_id")
    private UUID questionId;

    @TableField("answer")
    private String answer;

    @TableField("sources")
    private String sources;

    @TableField("confidence")
    private Float confidence;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}