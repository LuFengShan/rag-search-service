package com.example.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.document.MarkdownFrontmatterExtractor;
import com.example.rag.dto.response.BulkUploadResponse;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
     * 处理单个文件上传的核心逻辑
     * <p>
     * 与 uploadDocument 逻辑一致，但不使用 @Transactional 注解，
     * 由调用方控制事务。单文件失败不会影响其他文件。
     *
     * @param file 上传的文件
     * @param knowledgeBaseId 目标知识库ID
     * @param userId 上传者用户ID
     * @param kb 已验证的知识库实体（避免重复查询）
     * @return 文档响应对象
     * @throws IOException 文件保存失败时抛出
     */
    private DocumentResponse doUploadFile(MultipartFile file, UUID knowledgeBaseId, UUID userId, KnowledgeBase kb) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);

        if (!isFormatAllowed(kb, fileExtension)) {
            throw new BusinessException("此知识库仅允许上传 " + getKbAllowedFormats(kb) + " 格式的文件");
        }

        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

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

        log.info("Document uploaded successfully: {}", document.getTitle());
        return DocumentResponse.fromEntity(document);
    }

    /**
     * 批量上传多个文件
     * <p>
     * 支持同时上传多个文件到指定知识库。每个文件独立处理，
     * 单个文件失败不影响其他文件上传。返回汇总结果。
     *
     * @param files 上传的文件列表
     * @param knowledgeBaseId 目标知识库ID
     * @param userId 上传者用户ID
     * @return 批量上传响应，包含成功/失败数量和详情
     */
    @Transactional
    public BulkUploadResponse uploadDocuments(List<MultipartFile> files, UUID knowledgeBaseId, UUID userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库", "id", knowledgeBaseId.toString());
        }

        BulkUploadResponse response = BulkUploadResponse.builder()
                .totalFiles(files.size())
                .successCount(0)
                .failCount(0)
                .successList(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                response.setFailCount(response.getFailCount() + 1);
                response.getErrors().add(BulkUploadResponse.UploadError.builder()
                        .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "未知文件")
                        .reason("文件为空")
                        .build());
                continue;
            }
            try {
                DocumentResponse docResponse = doUploadFile(file, knowledgeBaseId, userId, kb);
                response.setSuccessCount(response.getSuccessCount() + 1);
                response.getSuccessList().add(docResponse);
            } catch (Exception e) {
                log.error("批量上传失败: {} - {}", file.getOriginalFilename(), e.getMessage());
                response.setFailCount(response.getFailCount() + 1);
                response.getErrors().add(BulkUploadResponse.UploadError.builder()
                        .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "未知文件")
                        .reason(e.getMessage())
                        .build());
            }
        }

        log.info("批量上传完成: 总数={}, 成功={}, 失败={}",
                response.getTotalFiles(), response.getSuccessCount(), response.getFailCount());
        return response;
    }

    /**
     * 上传文件夹（ZIP压缩包）
     * <p>
     * 接收一个 ZIP 压缩包文件，解压后将其中的所有文档文件上传到指定知识库。
     * 支持嵌套目录结构，自动过滤目录条目和非文档文件。
     *
     * @param zipFile ZIP压缩包文件
     * @param knowledgeBaseId 目标知识库ID
     * @param userId 上传者用户ID
     * @return 批量上传响应
     */
    @Transactional
    public BulkUploadResponse uploadFolder(MultipartFile zipFile, UUID knowledgeBaseId, UUID userId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库", "id", knowledgeBaseId.toString());
        }

        String originalFilename = zipFile.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        if (!"zip".equalsIgnoreCase(extension)) {
            throw new BusinessException("文件夹上传仅支持 ZIP 压缩包格式");
        }

        BulkUploadResponse response = BulkUploadResponse.builder()
                .totalFiles(0)
                .successCount(0)
                .failCount(0)
                .successList(new ArrayList<>())
                .errors(new ArrayList<>())
                .build();

        try (InputStream is = zipFile.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {

            // 先读取所有文件条目到内存中
            List<ZipEntry> fileEntries = new ArrayList<>();
            byte[] zipBytes = zipFile.getBytes();

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                // 跳过 macOS 系统文件
                if (entryName.contains("__MACOSX") || entryName.startsWith(".")) {
                    zis.closeEntry();
                    continue;
                }
                fileEntries.add(entry);
            }
            zis.close(); // 先关闭，后面重新打开

            response.setTotalFiles(fileEntries.size());

            // 重新打开ZIP，逐个处理文件
            for (ZipEntry fileEntry : fileEntries) {
                String entryName = fileEntry.getName();
                String fileName = entryName.contains("/")
                        ? entryName.substring(entryName.lastIndexOf("/") + 1)
                        : entryName;

                if (fileName.isEmpty()) {
                    response.setFailCount(response.getFailCount() + 1);
                    response.getErrors().add(BulkUploadResponse.UploadError.builder()
                            .fileName(entryName).reason("无效的文件名").build());
                    continue;
                }

                // 从压缩包中读取该文件的字节内容
                try (InputStream freshIs = new ByteArrayInputStream(zipBytes);
                     ZipInputStream freshZis = new ZipInputStream(freshIs)) {

                    ZipEntry targetEntry;
                    while ((targetEntry = freshZis.getNextEntry()) != null) {
                        if (targetEntry.getName().equals(entryName)) {
                            byte[] fileBytes = freshZis.readAllBytes();
                            String fileExt = getFileExtension(fileName);

                            if (!isFormatAllowed(kb, fileExt)) {
                                response.setFailCount(response.getFailCount() + 1);
                                response.getErrors().add(BulkUploadResponse.UploadError.builder()
                                        .fileName(fileName)
                                        .reason("格式不支持: " + fileExt)
                                        .build());
                                break;
                            }

                            // 保存文件到磁盘
                            Path uploadPath = Paths.get(UPLOAD_DIR);
                            if (!Files.exists(uploadPath)) {
                                Files.createDirectories(uploadPath);
                            }
                            String storedFilename = UUID.randomUUID().toString() + "." + fileExt;
                            Path filePath = uploadPath.resolve(storedFilename);
                            Files.write(filePath, fileBytes);

                            Document document = Document.builder()
                                    .id(UUID.randomUUID())
                                    .title(fileName.replace("." + fileExt, ""))
                                    .filePath(filePath.toString())
                                    .fileType(fileExt)
                                    .fileSize(fileBytes.length)
                                    .knowledgeBaseId(knowledgeBaseId)
                                    .uploadedBy(userId)
                                    .status(Document.Status.UPLOADING)
                                    .build();

                            documentMapper.insert(document);
                            documentProcessService.processDocument(document);

                            response.setSuccessCount(response.getSuccessCount() + 1);
                            response.getSuccessList().add(DocumentResponse.fromEntity(document));
                            break;
                        }
                        freshZis.closeEntry();
                    }
                } catch (Exception e) {
                    log.error("ZIP文件解压上传失败: {} - {}", fileName, e.getMessage());
                    response.setFailCount(response.getFailCount() + 1);
                    response.getErrors().add(BulkUploadResponse.UploadError.builder()
                            .fileName(fileName).reason(e.getMessage()).build());
                }
            }

        } catch (IOException e) {
            log.error("读取ZIP文件失败", e);
            throw new BusinessException("无法读取压缩文件: " + e.getMessage());
        }

        log.info("文件夹上传完成: 总数={}, 成功={}, 失败={}",
                response.getTotalFiles(), response.getSuccessCount(), response.getFailCount());
        return response;
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
     * 下载文档文件
     * <p>
     * 根据文档ID查找文档记录，读取磁盘上的文件并返回。
     * 返回结果包含文件字节数组、文件名和 MIME 类型。
     *
     * @param id 文档UUID
     * @return 下载信息（字节数组、文件名、内容类型）
     * @throws ResourceNotFoundException 当文档不存在时抛出
     * @throws IOException 当文件读取失败时抛出
     */
    public DownloadInfo downloadDocument(UUID id) throws IOException {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new ResourceNotFoundException("文档", "id", id.toString());
        }

        Path filePath = Paths.get(document.getFilePath());
        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException("文件", "path", document.getFilePath());
        }

        byte[] fileBytes = Files.readAllBytes(filePath);
        String originalFilename = document.getTitle();
        String extension = document.getFileType();
        if (extension != null && !extension.isEmpty() && !originalFilename.endsWith("." + extension)) {
            originalFilename = originalFilename + "." + extension;
        }

        String mimeType = determineMimeType(extension);

        log.info("Document downloaded: {}", document.getTitle());
        return new DownloadInfo(fileBytes, originalFilename, mimeType);
    }

    /**
     * 根据文件扩展名确定 MIME 类型
     */
    private String determineMimeType(String extension) {
        if (extension == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return switch (extension.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "xls" -> "application/vnd.ms-excel";
            case "txt", "md", "markdown", "csv", "log" -> "text/plain; charset=UTF-8";
            case "json" -> "application/json";
            case "xml", "html", "htm" -> "text/xml";
            case "zip" -> "application/zip";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    /**
     * 文件下载信息封装
     */
    public record DownloadInfo(byte[] content, String filename, String mimeType) {}

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

        String title = document.getTitle();
        String filePath = document.getFilePath();

        vectorService.deleteByDocumentId(id);
        documentMapper.deleteById(id);

        if (filePath != null && !filePath.isEmpty()) {
            try {
                Files.deleteIfExists(Path.of(filePath));
                log.debug("File deleted from disk: {}", filePath);
            } catch (IOException e) {
                log.warn("Failed to delete file [{}]: {}", filePath, e.getMessage());
            }
        }

        log.info("Document deleted: id={}, title={}", id, title);
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