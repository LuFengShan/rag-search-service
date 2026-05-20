package com.example.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.dto.response.DocumentResponse;
import com.example.rag.dto.response.PagedResponse;
import com.example.rag.entity.Document;
import com.example.rag.exception.BusinessException;
import com.example.rag.exception.ResourceNotFoundException;
import com.example.rag.mapper.DocumentMapper;
import com.example.rag.mapper.KnowledgeBaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private final VectorService vectorService;

    private final DocumentParserService documentParserService;

    private final DocumentProcessService documentProcessService;

    private static final String UPLOAD_DIR = "./uploads/documents/";

    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file, UUID knowledgeBaseId, UUID userId) throws IOException {
        if (knowledgeBaseMapper.selectById(knowledgeBaseId) == null) {
            throw new ResourceNotFoundException("知识库", "id", knowledgeBaseId.toString());
        }

        String originalFilename = file.getOriginalFilename();
        if (!documentParserService.isSupported(originalFilename)) {
            throw new BusinessException("不支持的文件格式");
        }

        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileExtension = getFileExtension(originalFilename);
        String storedFilename = UUID.randomUUID().toString() + "." + fileExtension;
        Path filePath = uploadPath.resolve(storedFilename);

        Files.copy(file.getInputStream(), filePath);

        Document document = Document.builder()
                .id(UUID.randomUUID())
                .title(originalFilename != null ? originalFilename.replace("." + fileExtension, "") : "Untitled")
                .filePath(filePath.toString())
                .fileType(fileExtension)
                .fileSize((int) file.getSize())
                .knowledgeBaseId(knowledgeBaseId)
                .uploadedBy(userId)
                .status(Document.Status.UPLOADING)
                .build();

        documentMapper.insert(document);

        documentProcessService.processDocument(document);

        return DocumentResponse.fromEntity(document);
    }

    public PagedResponse<DocumentResponse> getDocuments(int page, int pageSize, UUID knowledgeBaseId, String search) {
        Page<Document> pageParam = new Page<>(page + 1, pageSize);
        IPage<Document> documentPage;

        if (knowledgeBaseId != null && search != null && !search.isEmpty()) {
            documentPage = documentMapper.findByKnowledgeBaseIdAndTitleContaining(pageParam, knowledgeBaseId, search);
        } else if (knowledgeBaseId != null) {
            documentPage = documentMapper.findByKnowledgeBaseId(pageParam, knowledgeBaseId);
        } else if (search != null && !search.isEmpty()) {
            documentPage = documentMapper.findByTitleContaining(pageParam, search);
        } else {
            LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(Document::getCreatedAt);
            documentPage = documentMapper.selectPage(pageParam, wrapper);
        }

        return PagedResponse.<DocumentResponse>builder()
                .list(documentPage.getRecords().stream().map(doc -> {
                    DocumentResponse response = DocumentResponse.fromEntity(doc);
                    long chunkCount = vectorService.countByDocumentId(doc.getId());
                    response.setChunkCount((int) chunkCount);
                    return response;
                }).toList())
                .total(documentPage.getTotal())
                .page(page)
                .pageSize(pageSize)
                .totalPages((int) documentPage.getPages())
                .build();
    }

    public DocumentResponse getDocumentById(UUID id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new ResourceNotFoundException("文档", "id", id.toString());
        }

        DocumentResponse response = DocumentResponse.fromEntity(document);
        long chunkCount = vectorService.countByDocumentId(id);
        response.setChunkCount((int) chunkCount);
        return response;
    }

    @Transactional
    public void deleteDocument(UUID id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new ResourceNotFoundException("文档", "id", id.toString());
        }

        try {
            Path filePath = Paths.get(document.getFilePath());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", document.getFilePath());
        }

        vectorService.deleteByDocumentId(id);
        documentMapper.deleteById(id);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}