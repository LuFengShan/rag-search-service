package com.example.rag.controller;

import com.example.rag.dto.request.CreateKnowledgeBaseRequest;
import com.example.rag.dto.request.UpdateKnowledgeBaseRequest;
import com.example.rag.dto.response.ApiResponse;
import com.example.rag.dto.response.KnowledgeBaseResponse;
import com.example.rag.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Tag(name = "知识库管理", description = "知识库CRUD接口")
@PreAuthorize("hasAnyRole('ADMIN', 'KNOWLEDGE_BASE_ADMIN')")
public class KnowledgeBaseController extends BaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    @Operation(summary = "创建知识库", description = "创建新的知识库")
    public ResponseEntity<ApiResponse<KnowledgeBaseResponse>> createKnowledgeBase(
            @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        
        UUID userId = getCurrentUserId();
        KnowledgeBaseResponse response = knowledgeBaseService.createKnowledgeBase(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success("创建成功", response));
    }

    @GetMapping
    @Operation(summary = "获取知识库列表", description = "获取所有知识库")
    public ResponseEntity<ApiResponse<List<KnowledgeBaseResponse>>> listKnowledgeBases() {
        List<KnowledgeBaseResponse> response = knowledgeBaseService.getAllKnowledgeBases();
        return ResponseEntity.ok(success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取知识库详情", description = "根据ID获取知识库信息")
    public ResponseEntity<ApiResponse<KnowledgeBaseResponse>> getKnowledgeBase(@PathVariable UUID id) {
        KnowledgeBaseResponse response = knowledgeBaseService.getKnowledgeBaseById(id);
        return ResponseEntity.ok(success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识库", description = "更新知识库信息")
    public ResponseEntity<ApiResponse<KnowledgeBaseResponse>> updateKnowledgeBase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        KnowledgeBaseResponse response = knowledgeBaseService.updateKnowledgeBase(id, request);
        return ResponseEntity.ok(success("更新成功", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库", description = "删除指定知识库")
    public ResponseEntity<ApiResponse<Void>> deleteKnowledgeBase(@PathVariable UUID id) {
        knowledgeBaseService.deleteKnowledgeBase(id);
        return ResponseEntity.ok(success("删除成功", null));
    }
}