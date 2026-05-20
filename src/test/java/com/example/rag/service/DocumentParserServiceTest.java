package com.example.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Apache Tika 文档解析服务测试
 *
 * 测试 DocumentParserService 的各项功能
 */
class DocumentParserServiceTest {

    private DocumentParserService documentParserService;

    @BeforeEach
    void setUp() {
        documentParserService = new DocumentParserService();
    }

    // ==================== 文件格式支持测试 ====================

    @Test
    @DisplayName("测试TXT文件格式支持")
    void testIsSupported_TxtFile() {
        assertTrue(documentParserService.isSupported("document.txt"));
        assertTrue(documentParserService.isSupported("DOCUMENT.TXT"));
        assertTrue(documentParserService.isSupported("文档.txt"));
    }

    @Test
    @DisplayName("测试Markdown文件格式支持")
    void testIsSupported_MarkdownFile() {
        assertTrue(documentParserService.isSupported("readme.md"));
        assertTrue(documentParserService.isSupported("文档.MD"));
        assertTrue(documentParserService.isSupported("guide.markdown"));
    }

    @Test
    @DisplayName("测试CSV文件格式支持")
    void testIsSupported_CsvFile() {
        assertTrue(documentParserService.isSupported("data.csv"));
        assertTrue(documentParserService.isSupported("report.CSV"));
    }

    @Test
    @DisplayName("测试HTML文件格式支持")
    void testIsSupported_HtmlFile() {
        assertTrue(documentParserService.isSupported("page.html"));
        assertTrue(documentParserService.isSupported("page.htm"));
    }

    @Test
    @DisplayName("测试Word文档格式支持")
    void testIsSupported_WordFile() {
        assertTrue(documentParserService.isSupported("document.docx"));
        assertTrue(documentParserService.isSupported("report.doc"));
    }

    @Test
    @DisplayName("测试PDF文件格式支持")
    void testIsSupported_PdfFile() {
        assertTrue(documentParserService.isSupported("document.pdf"));
        assertTrue(documentParserService.isSupported("REPORT.PDF"));
    }

    @Test
    @DisplayName("测试Excel文件格式支持")
    void testIsSupported_ExcelFile() {
        assertTrue(documentParserService.isSupported("data.xlsx"));
        assertTrue(documentParserService.isSupported("report.xls"));
    }

    @Test
    @DisplayName("测试不支持的文件格式")
    void testIsSupported_UnsupportedFile() {
        assertFalse(documentParserService.isSupported("document.exe"));
        assertFalse(documentParserService.isSupported("script.bat"));
        assertFalse(documentParserService.isSupported("file.dll"));
        assertFalse(documentParserService.isSupported("noextension"));
    }

    @Test
    @DisplayName("测试空文件名")
    void testIsSupported_EmptyFilename() {
        assertFalse(documentParserService.isSupported(""));
        assertFalse(documentParserService.isSupported(null));
    }

    // ==================== 纯文本解析测试 ====================

    @Test
    @DisplayName("测试解析纯文本文件")
    void testParseDocument_PlainText() throws IOException {
        String content = "这是一个测试文档。\n第二行内容。\n\n第三段落。";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "test.txt");

        assertNotNull(result);
        assertTrue(result.contains("测试文档"));
        assertTrue(result.contains("第二行内容"));
        assertTrue(result.contains("第三段落"));
    }

    @Test
    @DisplayName("测试解析空文本文件")
    void testParseDocument_EmptyText() throws IOException {
        String content = "";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "empty.txt");

        assertNotNull(result);
        assertEquals("", result);
    }

    @Test
    @DisplayName("测试解析只有空白字符的文本")
    void testParseDocument_OnlyWhitespace() throws IOException {
        String content = "   \n\n   \n\t\t   ";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "whitespace.txt");

        assertNotNull(result);
        assertTrue(result.isEmpty() || result.trim().isEmpty());
    }

    @Test
    @DisplayName("测试解析中英文混合文本")
    void testParseDocument_MixedLanguage() throws IOException {
        String content = "Hello World!\n欢迎使用RAG系统。\nThis is a test.";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "mixed.txt");

        assertNotNull(result);
        assertTrue(result.contains("Hello"));
        assertTrue(result.contains("World"));
        assertTrue(result.contains("RAG"));
        assertTrue(result.contains("欢迎"));
    }

    @Test
    @DisplayName("测试解析特殊字符文本")
    void testParseDocument_SpecialCharacters() throws IOException {
        String content = "特殊字符: @#$%^&*()\n中文标点：，。！？\n英文标点: .,!?";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "special.txt");

        assertNotNull(result);
        assertTrue(result.contains("@"));
        assertTrue(result.contains("#"));
        assertTrue(result.contains("，"));
        assertTrue(result.contains("。"));
    }

    // ==================== 文本清理测试 ====================

    @Test
    @DisplayName("测试文本清理-合并多余空行")
    void testParseDocument_CleanExtraNewlines() throws IOException {
        String content = "第一段\n\n\n\n第二段\n\n\n\n\n第三段";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "extra-newlines.txt");

        // 多个连续空行应该被合并为单个空行
        assertNotNull(result);
        String[] lines = result.split("\n");
        // 统计连续空行数量
        int maxConsecutiveNewlines = 0;
        int currentConsecutive = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                currentConsecutive++;
                maxConsecutiveNewlines = Math.max(maxConsecutiveNewlines, currentConsecutive);
            } else {
                currentConsecutive = 0;
            }
        }
        assertTrue(maxConsecutiveNewlines <= 2, "多余空行应该被清理");
    }

    @Test
    @DisplayName("测试文本清理-移除行首行尾空白")
    void testParseDocument_TrimWhitespace() throws IOException {
        String content = "   第一行内容   \n\t第二行内容\t\n   第三行内容   ";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "whitespace.txt");

        assertNotNull(result);
        assertFalse(result.startsWith("   "), "行首空白应该被移除");
    }

    @Test
    @DisplayName("测试文本清理-移除控制字符")
    void testParseDocument_RemoveControlCharacters() throws IOException {
        String content = "第一行\u0000第二行\u0001第三行";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "control-chars.txt");

        assertNotNull(result);
        assertFalse(result.contains("\u0000"), "控制字符应该被移除");
        assertFalse(result.contains("\u0001"), "控制字符应该被移除");
    }

    // ==================== MIME类型检测测试 ====================

    @Test
    @DisplayName("测试检测纯文本MIME类型")
    void testDetectMediaType_PlainText() throws IOException {
        String content = "Hello World";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String mimeType = documentParserService.detectMediaType(inputStream, "test.txt");

        assertNotNull(mimeType);
        assertTrue(mimeType.contains("text/plain"));
    }

    @Test
    @DisplayName("测试检测HTML MimeType")
    void testDetectMediaType_Html() throws IOException {
        String content = "<html><body>Test</body></html>";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String mimeType = documentParserService.detectMediaType(inputStream, "test.html");

        assertNotNull(mimeType);
        assertTrue(mimeType.contains("html"));
    }

    @Test
    @DisplayName("测试检测JSON MimeType")
    void testDetectMediaType_Json() throws IOException {
        String content = "{\"name\": \"test\", \"value\": 123}";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String mimeType = documentParserService.detectMediaType(inputStream, "test.json");

        assertNotNull(mimeType);
        assertTrue(mimeType.contains("json"));
    }

    // ==================== 大文件测试 ====================

    @Test
    @DisplayName("测试解析较大文本文件")
    void testParseDocument_LargeText() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("这是第").append(i).append("行的测试内容。\n");
        }
        String content = sb.toString();
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "large.txt");

        assertNotNull(result);
        assertTrue(result.contains("第0行"));
        assertTrue(result.contains("第50行"));
        assertTrue(result.contains("第99行"));
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("测试null文件名")
    void testParseDocument_NullFilename() throws IOException {
        String content = "Test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, null);

        assertNotNull(result);
        assertEquals("Test content", result);
    }

    @Test
    @DisplayName("测试只有扩展名的文件名")
    void testParseDocument_OnlyExtension() throws IOException {
        String content = "Test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, ".txt");

        assertNotNull(result);
    }

    @Test
    @DisplayName("测试多级路径的文件名")
    void testParseDocument_PathWithDirectories() throws IOException {
        String content = "Test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "/path/to/document.txt");

        assertNotNull(result);
        assertEquals("Test content", result);
    }

    // ==================== 编码测试 ====================

    @Test
    @DisplayName("测试UTF-8编码解析")
    void testParseDocument_Utf8Encoding() throws IOException {
        String content = "中文测试🍀emoji测试";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = documentParserService.parseDocument(inputStream, "emoji.txt");

        assertNotNull(result);
        assertTrue(result.contains("中文"));
        assertTrue(result.contains("emoji"));
    }

    @Test
    @DisplayName("测试长文件名处理")
    void testParseDocument_LongFilename() throws IOException {
        String content = "Test content";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        String longFilename = "a".repeat(100) + ".txt";

        String result = documentParserService.parseDocument(inputStream, longFilename);

        assertNotNull(result);
    }
}
