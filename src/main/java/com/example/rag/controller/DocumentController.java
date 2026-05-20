package com.example.rag.controller;

import com.example.rag.dto.response.ApiResponse;
import com.example.rag.dto.response.DocumentResponse;
import com.example.rag.dto.response.PagedResponse;
import com.example.rag.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "文档上传和管理接口")
@PreAuthorize("hasAnyRole('ADMIN', 'KNOWLEDGE_BASE_ADMIN')")
public class DocumentController extends BaseController {

    private final DocumentService documentService;

    @PostMapping(value="/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档", description = "上传文档到指定知识库")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId) throws IOException {
        
        UUID userId = getCurrentUserId();
        DocumentResponse response = documentService.uploadDocument(file, knowledgeBaseId, userId);
        return ResponseEntity.ok(success("上传成功", response));
    }

    @GetMapping
    @Operation(summary = "获取文档列表", description = "分页获取文档列表")
    public ResponseEntity<ApiResponse<PagedResponse<DocumentResponse>>> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) UUID knowledgeBaseId,
            @RequestParam(required = false) String search) {
        PagedResponse<DocumentResponse> response = documentService.getDocuments(page, pageSize, knowledgeBaseId, search);
        return ResponseEntity.ok(success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取文档详情", description = "根据ID获取文档信息")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(@PathVariable UUID id) {
        DocumentResponse response = documentService.getDocumentById(id);
        return ResponseEntity.ok(success(response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档", description = "删除指定文档")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(success("删除成功", null));
    }
}