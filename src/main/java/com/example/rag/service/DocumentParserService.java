package com.example.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文档解析服务
 * 使用 Apache Tika 解析各种格式的文档
 *
 * 支持的格式：
 * - 文档：DOC, DOCX, ODT, RTF
 * - PDF：PDF
 * - 表格：XLS, XLSX, CSV
 * - 演示文稿：PPT, PPTX
 * - 文本：TXT, HTML, XML, Markdown
 * - 其他：JSON, EPUB, Mail
 */
@Slf4j
@Service
public class DocumentParserService {

    /** Tika 文档解析器（自动检测文档类型） */
    private final Tika tika = new Tika();

    /** 自动检测解析器 */
    private final AutoDetectParser parser = new AutoDetectParser();

    /** 支持的纯文本扩展名 */
    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "txt", "md", "markdown", "csv", "log", "json", "xml", "html", "htm"
    ));

    /** 默认最大文本长度（10MB） */
    private static final int DEFAULT_MAX_TEXT_LENGTH = 10 * 1024 * 1024;

    /**
     * 解析文档文件
     *
     * @param inputStream 文档输入流
     * @param filename    文件名（用于自动检测类型）
     * @return 提取的文本内容
     */
    public String parseDocument(InputStream inputStream, String filename) {
        String extension = getFileExtension(filename).toLowerCase();

        if (TEXT_EXTENSIONS.contains(extension)) {
            return parseAsText(inputStream);
        }

        return parseWithTika(inputStream, filename);
    }

    /**
     * 解析文档文件（自动重试模式）
     * 先尝试 Tika 解析，失败时对 ZIP 类格式（epub/docx/xlsx等）使用回退解析
     *
     * @param filePath 文件路径
     * @param filename 文件名
     * @return 提取的文本内容
     */
    public String parseDocument(String filePath, String filename) {
        String extension = getFileExtension(filename).toLowerCase();

        if (TEXT_EXTENSIONS.contains(extension)) {
            try {
                return parseAsText(Files.newInputStream(Paths.get(filePath)));
            } catch (IOException e) {
                log.error("Failed to read text file: {}", filePath, e);
                return "";
            }
        }

        try {
            String content = parseWithTika(Files.newInputStream(Paths.get(filePath)), filename);
            if (!content.isEmpty()) {
                return content;
            }
        } catch (Exception e) {
            log.warn("Primary parsing failed for: {}, trying archive fallback", filename);
        }

        return parseZipArchiveAsText(filePath);
    }

    /**
     * 使用 Tika 解析文档
     *
     * @param inputStream 文档输入流
     * @param filename    文件名
     * @return 提取的文本内容
     */
    private String parseWithTika(InputStream inputStream, String filename) {
        try {
            BodyContentHandler handler = new BodyContentHandler(DEFAULT_MAX_TEXT_LENGTH);

            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

            ParseContext context = new ParseContext();

            parser.parse(inputStream, handler, metadata, context);

            String content = handler.toString();

            content = cleanText(content);

            log.info("Successfully parsed document: {}, extracted {} characters",
                    filename, content.length());

            return content;

        } catch (IOException | SAXException | TikaException e) {
            log.warn("Failed to parse document: {}, reason: {}", filename, e.getMessage());
            return "";
        }
    }

    /**
     * 解析纯文本文件
     *
     * @param inputStream 文件输入流
     * @return 文件内容
     */
    private String parseAsText(InputStream inputStream) {
        try {
            String content = new String(inputStream.readAllBytes());
            return cleanText(content);
        } catch (IOException e) {
            log.error("Failed to read text file", e);
            throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 ZIP 归档文件（EPUB/DOCX/XLSX等）中提取文本
     * 遍历归档中的所有条目，读取文本内容文件
     *
     * @param filePath 文件路径
     * @return 提取的文本内容
     */
    private String parseZipArchiveAsText(String filePath) {
        StringBuilder allText = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Paths.get(filePath)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().toLowerCase();
                if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml")
                        || name.endsWith(".xml") || name.endsWith(".txt")) {
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }
                        String text = baos.toString(StandardCharsets.UTF_8);
                        text = text.replaceAll("<[^>]+>", " ");
                        allText.append(text).append("\n");
                    } catch (Exception e) {
                        log.debug("Skipped unreadable entry in archive: {}", entry.getName());
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.warn("Failed to read archive as ZIP: {}, reason: {}", filePath, e.getMessage());
            return "";
        }

        String result = cleanText(allText.toString());
        if (!result.isEmpty()) {
            log.info("Extracted {} characters from archive: {}", result.length(), filePath);
        }
        return result;
    }

    /**
     * 检测文档类型（MIME 类型）
     *
     * @param inputStream 文档输入流
     * @param filename    文件名
     * @return MIME 类型
     */
    public String detectMediaType(InputStream inputStream, String filename) {
        try {
            return tika.detect(inputStream, filename);
        } catch (IOException e) {
            log.error("Failed to detect media type for: {}", filename, e);
            return "application/octet-stream";
        }
    }

    /**
     * 提取文档元数据
     *
     * @param inputStream 文档输入流
     * @param filename    文件名
     * @return 元数据映射
     */
    public Metadata extractMetadata(InputStream inputStream, String filename) {
        try {
            BodyContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

            ParseContext context = new ParseContext();
            parser.parse(inputStream, handler, metadata, context);

            return metadata;

        } catch (IOException | SAXException | TikaException e) {
            log.error("Failed to extract metadata from: {}", filename, e);
            return new Metadata();
        }
    }

    /**
     * 清理文本内容
     * 移除多余的空白字符、规范换行符
     *
     * @param text 原始文本
     * @return 清理后的文本
     */
    private String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 移除零宽字符和控制字符
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

        // 规范化换行符
        text = text.replaceAll("\\r\\n", "\n");
        text = text.replaceAll("\\r", "\n");

        // 合并多个连续空行为单个空行
        text = text.replaceAll("\\n{3,}", "\n\n");

        // 移除行首行尾多余空白
        text = text.replaceAll("[ \\t]+\n", "\n");
        text = text.replaceAll("\\n[ \\t]+", "\n");

        return text.trim();
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名（小写，不含点）
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * 检查是否为支持的文档类型
     *
     * @param filename 文件名
     * @return 是否支持
     */
    public boolean isSupported(String filename) {
        String extension = getFileExtension(filename).toLowerCase();

        // 支持的格式列表
        List<String> supportedExtensions = Arrays.asList(
                // 文本格式
                "txt", "md", "markdown", "csv", "log", "json", "xml", "html", "htm",
                // Word 文档
                "doc", "docx",
                // PDF
                "pdf",
                // Excel
                "xls", "xlsx",
                // PowerPoint
                "ppt", "pptx",
                // OpenDocument
                "odt", "ods", "odp",
                // 富文本
                "rtf",
                // 其他
                "eml", "msg", "epub"
        );

        return supportedExtensions.contains(extension);
    }
}
