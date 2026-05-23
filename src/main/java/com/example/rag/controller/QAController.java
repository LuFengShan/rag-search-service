package com.example.rag.controller;

import com.example.rag.dto.request.QuestionRequest;
import com.example.rag.dto.response.*;
import com.example.rag.entity.Question;
import com.example.rag.service.QAService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
@Tag(name = "问答服务", description = "智能问答接口")
@PreAuthorize("isAuthenticated()")
public class QAController extends BaseController {

    private final QAService qaService;

    @PostMapping("/question")
    @Operation(summary = "提问", description = "提交问题获取答案，支持多轮会话")
    public ResponseEntity<ApiResponse<AnswerResponse>> askQuestion(
            @Valid @RequestBody QuestionRequest request) {
        
        UUID userId = getCurrentUserId();
        AnswerResponse response = qaService.askQuestion(request, userId);
        return ResponseEntity.ok(success(response));
    }

    @GetMapping("/sessions")
    @Operation(summary = "获取会话列表", description = "获取当前用户的所有对话会话")
    public ResponseEntity<ApiResponse<List<ConversationSession>>> getSessions() {
        UUID userId = getCurrentUserId();
        List<ConversationSession> sessions = qaService.getSessions(userId);
        return ResponseEntity.ok(success(sessions));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "获取会话消息", description = "获取指定会话的所有问答消息")
    public ResponseEntity<ApiResponse<List<ConversationMessage>>> getSessionMessages(
            @PathVariable UUID sessionId) {
        List<ConversationMessage> messages = qaService.getSessionMessages(sessionId);
        return ResponseEntity.ok(success(messages));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "删除会话", description = "删除指定会话及其所有问答记录")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable UUID sessionId) {
        qaService.deleteSession(sessionId);
        return ResponseEntity.ok(success("会话已删除", null));
    }

    @GetMapping("/history")
    @Operation(summary = "获取对话历史", description = "分页获取用户的提问历史")
    public ResponseEntity<ApiResponse<PagedResponse<Question>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        UUID userId = getCurrentUserId();
        PagedResponse<Question> response = qaService.getQuestionHistory(userId, page, pageSize);
        return ResponseEntity.ok(success(response));
    }
}
