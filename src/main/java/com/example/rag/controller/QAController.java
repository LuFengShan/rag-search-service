package com.example.rag.controller;

import com.example.rag.dto.request.QuestionRequest;
import com.example.rag.dto.response.AnswerResponse;
import com.example.rag.dto.response.ApiResponse;
import com.example.rag.dto.response.PagedResponse;
import com.example.rag.entity.Question;
import com.example.rag.service.QAService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
@Tag(name = "问答服务", description = "智能问答接口")
@PreAuthorize("isAuthenticated()")
public class QAController extends BaseController {

    private final QAService qaService;

    @PostMapping("/question")
    @Operation(summary = "提问", description = "提交问题获取答案")
    public ResponseEntity<ApiResponse<AnswerResponse>> askQuestion(
            @Valid @RequestBody QuestionRequest request) {
        
        UUID userId = getCurrentUserId();
        AnswerResponse response = qaService.askQuestion(request, userId);
        return ResponseEntity.ok(success(response));
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