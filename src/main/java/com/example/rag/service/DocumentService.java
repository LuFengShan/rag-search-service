package com.example.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.document.MarkdownFrontmatterExtractor;
import com.example.rag.dto.response.DocumentResponse;
import com.example.rag.dto.response.PagedResponse;
import com.example.rag.entity.Document;
import com.example.rag.entity.KnowledgeBase;
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

/**
 * 文档服务层
 * <p>
 * 负责文档的上传、查询、删除等核心业务逻辑。
 * 文档上传后会触发异步处理流程，将文档解析、分块、向量化并存入数据库。
 * </p>
 *
 * <h3>文档上传流程</h3>
 * <pre>
 * 1. 验证知识库存在
 * 2. 验证文件格式允许
 * 3. 保存文件到磁盘
 * 4. 插入 document 记录（状态=UPLOADING）
 * 5. 调用异步处理（解析→分块→向量化）
 * </pre>
 *
 * <h3>文件存储结构</h3>
 * <pre>
 * ./uploads/documents/
 *   ├── {UUID}.pdf
 *   ├── {UUID}.md
 *   ├── {UUID}.txt
 *   └── ...
 * </pre>
 *
 * @see com.example.rag.service.DocumentProcessService 文档异步处理服务
 * @see com.example.rag.service.DocumentParserService 文档解析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    /** 文档数据访问层 */
    private final DocumentMapper documentMapper;

    /** 知识库数据访问层 */
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /** 向量服务 */
    private final VectorService vectorService;

    /** 文档解析服务 */
    private final DocumentParserService documentParserService;

    /** 文档异步处理服务 */
    private final DocumentProcessService documentProcessService;

    /** 文件上传目录 */
    private static final String UPLOAD_DIR = "./uploads/documents/";

    /**
     * 上传文档
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>验证知识库存在</li>
     *   <li>验证文件格式是否允许（根据知识库配置）</li>
     *   <li>创建上传目录（如果不存在）</li>
     *   <li>保存文件到磁盘（UUID命名）</li>
     *   <li>插入数据库记录</li>
     *   <li>触发异步处理流程</li>
     * </ol>
     *
     * @param file 上传的文件
     * @param knowledgeBaseId 目标知识库ID
     * @param userId 上传者用户ID
     * @return 文档响应对象
     * @throws IOException 文件保存失败时抛出
     * @throws ResourceNotFoundException 知识库不存在时抛出
     * @throws BusinessException 文件格式不允许时抛出
     */
    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file, UUID knowledgeBaseId, UUID userId) throws IOException {
        // 验证知识库存在
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库", "id", knowledgeBaseId.toString());
        }

        // 获取文件名和扩展名
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);

        // 验证文件格式是否允许
        if (!isFormatAllowed(kb, fileExtension)) {
            throw new BusinessException("此知识库仅允许上传 " + getKbAllowedFormats(kb) + " 格式的文件");
        }

        // 创建上传目录（如果不存在）
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成唯一文件名并保存文件
        String storedFilename = UUID.randomUUID().toString() + "." + fileExtension;
        Path filePath = uploadPath.resolve(storedFilename);
        Files.copy(file.getInputStream(), filePath);

        // 构建文档实体
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

        // 插入数据库
        documentMapper.insert(document);

        // 触发异步处理（解析、分块、向量化）
        documentProcessService.processDocument(document);

        log.info("Document uploaded successfully: {}", document.getTitle());
        return DocumentResponse.fromEntity(document);
    }

    /**
     * 检查文件格式是否允许上传到指定知识库
     *
     * @param kb 知识库
     * @param fileExtension 文件扩展名（不含点）
     * @return true 如果允许，false 否则
     */
    private boolean isFormatAllowed(KnowledgeBase kb, String fileExtension) {
        String config = kb.getConfig();
        // CAR_MD 类型知识库只允许 Markdown 文件
        if (config != null && config.contains("\"docType\":\"CAR_MD\"")) {
            return "md".equalsIgnoreCase(fileExtension) || "markdown".equalsIgnoreCase(fileExtension);
        }
        // 其他知识库允许所有支持的格式
        return documentParserService.isSupported(originalFilename(fileExtension));
    }

    /**
     * 获取知识库允许的文件格式描述
     *
     * @param kb 知识库
     * @return 格式描述字符串
     */
    private String getKbAllowedFormats(KnowledgeBase kb) {
        if (kb.getConfig() != null && kb.getConfig().contains("\"docType\":\"CAR_MD\"")) {
            return "Markdown (.md)";
        }
        return "PDF/Word/PPT/TXT 等";
    }

    /**
     * 构建虚拟文件名（用于格式检查）
     *
     * @param extension 文件扩展名
     * @return 虚拟文件名
     */
    private String originalFilename(String extension) {
        return "file." + extension;
    }

    /**
     * 分页查询文档列表
     * <p>
     * 支持按知识库ID过滤和按标题搜索。
     *
     * @param page 页码（从0开始）
     * @param pageSize 每页大小
     * @param knowledgeBaseId 知识库ID（可选）
     * @param search 搜索关键词（可选）
     * @return 分页文档响应
     */
    public PagedResponse<DocumentResponse> getDocuments(int page, int pageSize, UUID knowledgeBaseId, String search) {
        Page<Document> pageParam = new Page<>(page + 1, pageSize);
        IPage<Document> documentPage;

        // 根据参数组合查询条件
        if (knowledgeBaseId != null && search != null && !search.isEmpty()) {
            // 按知识库ID和标题搜索
            documentPage = documentMapper.findByKnowledgeBaseIdAndTitleContaining(pageParam, knowledgeBaseId, search);
        } else if (knowledgeBaseId != null) {
            // 按知识库ID查询
            documentPage = documentMapper.findByKnowledgeBaseId(pageParam, knowledgeBaseId);
        } else if (search != null && !search.isEmpty()) {
            // 按标题搜索
            documentPage = documentMapper.findByTitleContaining(pageParam, search);
        } else {
            // 查询所有，按创建时间降序
            LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(Document::getCreatedAt);
            documentPage = documentMapper.selectPage(pageParam, wrapper);
        }

        // 转换为响应对象，附加分块数量
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

    /**
     * 根据ID获取文档详情
     *
     * @param id 文档UUID
     * @return 文档响应对象
     * @throws ResourceNotFoundException 当文档不存在时抛出
     */
    public DocumentResponse getDocumentById(UUID id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new ResourceNotFoundException("文档", "id", id.toString());
        }
        DocumentResponse response = DocumentResponse.fromEntity(document);
        // 查询分块数量
        long chunkCount = vectorService.countByDocumentId(id);
        response.setChunkCount((int) chunkCount);
        return response;
    }

    /**
     * 删除文档
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>验证文档存在</li>
     *   <li>删除磁盘上的文件</li>
     *   <li>删除关联的文档分块</li>
     *   <li>删除数据库记录</li>
     * </ol>
     *
     * @param id 文档UUID
     * @throws ResourceNotFoundException 当文档不存在时抛出
     */
    @Transactional
    public void deleteDocument(UUID id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new ResourceNotFoundException("文档", "id", id.toString());
        }

        // 删除磁盘文件
        try {
            Files.deleteIfExists(Paths.get(document.getFilePath()));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", document.getFilePath());
        }

        // 删除关联的文档分块
        vectorService.deleteByDocumentId(id);

        // 删除数据库记录
        documentMapper.deleteById(id);

        log.info("Document deleted successfully: {}", document.getTitle());
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名（小写，不含点）
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "bin";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}