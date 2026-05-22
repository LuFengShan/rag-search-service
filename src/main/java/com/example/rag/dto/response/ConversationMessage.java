package com.example.rag.dto.response;

import com.example.rag.entity.Question;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {
    private UUID questionId;
    private String question;
    private String answer;
    private String status;
    private String createdAt;
}
