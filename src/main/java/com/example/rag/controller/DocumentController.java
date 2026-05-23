package com.example.rag.controller;

import com.example.rag.dto.response.ApiResponse;
import com.example.rag.dto.response.BulkUploadResponse;
import com.example.rag.dto.response.DocumentResponse;
import com.example.rag.dto.response.PagedResponse;
import com.example.rag.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "文档上传和管理接口")
@PreAuthorize("hasAnyRole('ADMIN', 'KNOWLEDGE_BASE_ADMIN')")
public class DocumentController extends BaseController {

    private final DocumentService documentService;

    private static final String TEMPLATE_PATH = "./docs/车系MD模板示例-比亚迪秦PLUS.md";

    @GetMapping("/template")
    @Operation(summary = "下载车系MD模板", description = "下载卖车知识库的Markdown模板文件")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        Path path = Paths.get(TEMPLATE_PATH);
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        byte[] content = Files.readAllBytes(path);
        String filename = "车系MD模板-比亚迪秦PLUS.md";
        String encodedHeader = buildContentDisposition(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, encodedHeader)
                .contentType(MediaType.parseMediaType("text/markdown"))
                .body(content);
    }

    @PostMapping(value="/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文档", description = "上传文档到指定知识库")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId) throws IOException {
        
        UUID userId = getCurrentUserId();
        DocumentResponse response = documentService.uploadDocument(file, knowledgeBaseId, userId);
        return ResponseEntity.ok(success("上传成功", response));
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "批量上传文档", description = "同时上传多个文件到指定知识库，每个文件独立处理")
    public ResponseEntity<ApiResponse<BulkUploadResponse>> uploadDocuments(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId) {

        UUID userId = getCurrentUserId();
        BulkUploadResponse response = documentService.uploadDocuments(files, knowledgeBaseId, userId);
        return ResponseEntity.ok(success("批量上传完成", response));
    }

    @PostMapping(value = "/upload-folder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件夹", description = "上传ZIP压缩包，自动解压并将其中所有文档导入知识库")
    public ResponseEntity<ApiResponse<BulkUploadResponse>> uploadFolder(
            @RequestParam("file") MultipartFile zipFile,
            @RequestParam("knowledgeBaseId") UUID knowledgeBaseId) {

        UUID userId = getCurrentUserId();
        BulkUploadResponse response = documentService.uploadFolder(zipFile, knowledgeBaseId, userId);
        return ResponseEntity.ok(success("文件夹上传完成", response));
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

    @GetMapping("/{id}/download")
    @Operation(summary = "下载文档", description = "下载指定文档的原始文件")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID id) throws IOException {
        DocumentService.DownloadInfo info = documentService.downloadDocument(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(info.filename()))
                .contentType(MediaType.parseMediaType(info.mimeType()))
                .body(info.content());
    }

    /**
     * 构建支持中文文件名的 Content-Disposition 头
     * <p>
     * 使用 RFC 5987 标准：filename*=UTF-8''url-encoded-name 给现代浏览器，
     * filename 参数作为老浏览器降级。
     */
    private String buildContentDisposition(String filename) {
        String utf8Encoded = UriUtils.encode(filename, StandardCharsets.UTF_8);
        return "attachment; filename=\"" + utf8Encoded + "\"; filename*=UTF-8''" + utf8Encoded;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档", description = "删除指定文档")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(success("删除成功", null));
    }
}