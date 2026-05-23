package com.example.rag.service;

import com.example.rag.document.CarDocumentMetadata;
import com.example.rag.document.MarkdownFrontmatterExtractor;
import com.example.rag.entity.Document;
import com.example.rag.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 文档异步处理服务
 * <p>
 * 负责将上传的文档文件解析、分块、向量化并存入数据库。
 * 整个流程在独立线程池中异步执行，不阻塞用户的 HTTP 请求。
 * </p>
 *
 * <h3>车系 MD 文档的分块策略</h3>
 * <p>
 * 对带有 frontmatter 元数据的车系 Markdown 文档，采用"文档级元数据 + 二级标题语义分块"策略：
 * </p>
 * <ol>
 *   <li>从 frontmatter 提取品牌/车系/标签/竞品等元数据</li>
 *   <li>按 ## 二级标题拆分为独立章节（如"配置与价格""动力与油耗"）</li>
 *   <li>每个 chunk 携带文档元数据 + 章节标题前缀，形成自包含的语义单元</li>
 *   <li>超长章节（>6000 字符）按 ### 三级标题或段落边界做二次拆分</li>
 * </ol>
 *
 * <h3>状态机</h3>
 * UPLOADING → PROCESSING → INDEXED（成功） / FAILED（失败）
 *
 * @see DocumentService      文档上传入口
 * @see VectorService        向量生成与检索
 * @see DocumentParserService 文档内容提取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessService {

    /** 单个 chunk 的最大字符数（≈1500 tokens for Chinese） */
    private static final int MAX_CHUNK_CHARS = 6000;

    private final DocumentMapper documentMapper;
    private final VectorService vectorService;
    private final DocumentParserService documentParserService;

    // ==================== 入口：异步文档处理 ====================

    @Async("documentProcessExecutor")
    public void processDocument(Document document) {
        document.setStatus(Document.Status.PROCESSING);
        documentMapper.updateById(document);

        try {
            String content = documentParserService.parseDocument(
                    document.getFilePath(), document.getTitle());

            if (content == null || content.trim().isEmpty()) {
                log.warn("Document has no extractable content: {}", document.getId());
                document.setStatus(Document.Status.FAILED);
                documentMapper.updateById(document);
                return;
            }

            List<String> chunks = isMarkdown(document)
                    ? chunkMarkdownDocument(content)
                    : chunkGenericDocument(content);

            int chunkIndex = 0;
            for (String chunk : chunks) {
                if (!chunk.trim().isEmpty()) {
                    float[] embedding = vectorService.embed(chunk);
                    vectorService.saveDocumentChunk(document.getId(), chunkIndex, chunk, embedding);
                    chunkIndex++;
                }
            }

            log.info("Document processed: {} chunks for {}", chunkIndex, document.getId());
            document.setStatus(Document.Status.INDEXED);
            documentMapper.updateById(document);

        } catch (Exception e) {
            log.error("Failed to process document: {}", document.getId(), e);
            document.setStatus(Document.Status.FAILED);
            documentMapper.updateById(document);
        }
    }

    // ==================== Markdown 文档分块（车系专用） ====================

    /**
     * 对 Markdown 文档进行语义分块
     * <p>
     * 如果文档包含 frontmatter 元数据（车系文档），采用"元数据前缀 + ## 章节拆分"策略。
     * 否则退化为段落级通用分块。
     */
    private List<String> chunkMarkdownDocument(String content) {
        if (MarkdownFrontmatterExtractor.hasFrontmatter(content)) {
            return chunkCarMarkdown(content);
        }
        return chunkGenericDocument(MarkdownFrontmatterExtractor.extractBody(content));
    }

    /**
     * 车系 Markdown 文档的核心分块策略
     * <p>
     * 步骤：
     * <ol>
     *   <li>提取 frontmatter → CarDocumentMetadata → 构建元数据前缀</li>
     *   <li>分割正文：按 {@code ## } 二级标题切分为章节</li>
     *   <li>每个章节拼接"元数据前缀 + 章节标题 + 章节内容"</li>
     *   <li>超长章节（>MAX_CHUNK_CHARS）按三级标题或段落二次拆分</li>
     * </ol>
     *
     * <h3>拆分示例（凯美瑞文档）</h3>
     * <pre>
     * Chunk 1：元数据前缀 + "## 基本信息" + 尺寸/轴距/动力版本
     * Chunk 2：元数据前缀 + "## 配置与价格" + 价格表
     * Chunk 3：元数据前缀 + "## 动力与油耗" + 油耗数据
     * Chunk 4：元数据前缀 + "## 核心卖点" + 卖点列表
     * Chunk 5：元数据前缀 + "## 适合人群" + 用户画像
     * Chunk 6：元数据前缀 + "## 竞品对比" + 对比分析
     * </pre>
     */
    private List<String> chunkCarMarkdown(String content) {
        // 1. 提取元数据
        CarDocumentMetadata meta = MarkdownFrontmatterExtractor.extractCarMetadata(content);
        String body = MarkdownFrontmatterExtractor.extractBody(content);
        String metaPrefix = buildMetadataPrefix(meta);

        // 2. 按 ## 分割章节
        List<String> chapters = splitByHeading2(body);

        // 3. 为每个章节拼接元数据前缀
        List<String> chunks = new ArrayList<>();
        for (String chapter : chapters) {
            String enriched = metaPrefix + "\n\n" + chapter;
            if (enriched.length() <= MAX_CHUNK_CHARS) {
                chunks.add(enriched);
            } else {
                chunks.addAll(splitLongChapter(enriched, metaPrefix));
            }
        }

        logChunks(chunks, meta);
        return chunks;
    }

    // ==================== 元数据拼接 ====================

    /**
     * 构建文档级元数据前缀
     * <p>
     * 格式：
     * <pre>
     * 【品牌:丰田】【车系:凯美瑞】【车型:中型轿车】【能源:燃油/双擎混动】
     * 【价格:17.98-26.98万】【标签:商务家用、混动省油、B级标杆】
     * 【竞品:本田雅阁、大众帕萨特】【卖点:丰田双擎、舒适标杆、保值率高】
     * </pre>
     * 元数据随每个 chunk 一起 embedding，检索时能自然匹配到品牌/车系/标签等信息。
     */
    private String buildMetadataPrefix(CarDocumentMetadata meta) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(formatMeta("品牌", meta.getBrand(), meta.getModel()));
        joiner.add(formatMeta("车型", meta.getCarType(), meta.getFuelType()));
        joiner.add(formatMeta("价格", meta.getPriceRange()));
        joiner.add(formatMeta("标签", joinOrDefault(meta.getTags(), "")));
        joiner.add(formatMeta("竞品", joinOrDefault(meta.getCompetitors(), "")));
        joiner.add(formatMeta("卖点", meta.getSalesPoints()));
        joiner.add(formatMeta("适合人群", meta.getTargetUsers()));
        return joiner.toString();
    }

    private static String formatMeta(String label, String... values) {
        StringBuilder sb = new StringBuilder("【").append(label).append(":");
        boolean hasValue = false;
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                if (hasValue) sb.append("/");
                sb.append(v);
                hasValue = true;
            }
        }
        if (!hasValue) return "";
        return sb.append("】").toString();
    }

    private static String joinOrDefault(List<String> list, String defaultValue) {
        if (list == null || list.isEmpty()) return defaultValue;
        return String.join("、", list);
    }

    // ==================== 章节分割 ====================

    /**
     * 按 ## 二级标题拆分正文
     * <p>
     * 正则 {@code (?=\n##\s)} 在"换行 + ## + 空格"之前的位置切开，
     * 保留每个章节开头完整的 ## 标题行。
     */
    static List<String> splitByHeading2(String body) {
        List<String> chapters = new ArrayList<>();
        String[] sections = body.split("(?=\n##\\s)");
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;
            chapters.add(section);
        }
        return chapters;
    }

    // ==================== 超长章节二次拆分 ====================

    /**
     * 对超长章节进行二次拆分
     * <p>
     * 策略：
     * <ol>
     *   <li>有三级标题（###）→ 按三级标题拆分</li>
     *   <li>无三级标题 → 按段落边界（双空行）拆分，每个子块追加元数据前缀</li>
     *   <li>单个段落仍超长 → 按句子边界切分</li>
     * </ol>
     *
     * @param fullChapter 完整章节内容（已含元数据前缀）
     * @param metaPrefix  元数据前缀（用于追加到每个子块）
     * @return 拆分后的子块列表
     */
    private List<String> splitLongChapter(String fullChapter, String metaPrefix) {
        List<String> subChunks = new ArrayList<>();

        // 找到第一个 ## 之后的内容（去掉已包含的标题行）
        String bodyOnly = fullChapter;
        int headingEnd = fullChapter.indexOf("\n", fullChapter.indexOf("## "));
        if (headingEnd > 0) {
            String headingLine = fullChapter.substring(0, headingEnd);
            bodyOnly = fullChapter.substring(headingEnd).trim();

            // 有三级标题 → 按 ### 拆分
            if (bodyOnly.contains("\n### ")) {
                String[] subsections = bodyOnly.split("(?=\\n###\\s)");
                for (String sub : subsections) {
                    sub = sub.trim();
                    if (sub.isEmpty()) continue;
                    String enriched = metaPrefix + "\n\n" + headingLine + "\n" + sub;
                    if (enriched.length() <= MAX_CHUNK_CHARS) {
                        subChunks.add(enriched);
                    } else {
                        subChunks.addAll(splitByParagraphs(enriched, metaPrefix));
                    }
                }
                return subChunks;
            }

            // 无三级标题 → 按段落拆分
            return splitByParagraphs(fullChapter, metaPrefix);
        }

        return splitByParagraphs(fullChapter, metaPrefix);
    }

    /**
     * 按段落边界拆分长文本
     */
    private List<String> splitByParagraphs(String text, String metaPrefix) {
        List<String> parts = new ArrayList<>();
        String body = text;
        String heading = "";

        int headingEnd = text.indexOf("\n", text.indexOf("## "));
        if (headingEnd > 0) {
            heading = text.substring(0, headingEnd) + "\n";
            body = text.substring(headingEnd).trim();
        }

        StringBuilder current = new StringBuilder(metaPrefix + "\n\n" + heading);
        String[] paragraphs = body.split("\\n{2,}");

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            if (current.length() + para.length() + 2 > MAX_CHUNK_CHARS && current.length() > metaPrefix.length() + 10) {
                parts.add(current.toString().trim());
                current = new StringBuilder(metaPrefix + "\n\n" + heading);
            }
            current.append(para).append("\n\n");
        }

        if (current.length() > metaPrefix.length() + 10) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    // ==================== 通用文档分块（非 Markdown） ====================

    private List<String> chunkGenericDocument(String content) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\\n{2,}");

        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            if (current.length() + para.length() > MAX_CHUNK_CHARS && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(para);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    // ==================== 工具方法 ====================

    private static boolean isMarkdown(Document doc) {
        String type = doc.getFileType();
        return "md".equalsIgnoreCase(type) || "markdown".equalsIgnoreCase(type);
    }

    private void logChunks(List<String> chunks, CarDocumentMetadata meta) {
        log.info("Chunked car doc [{} {}] into {} chunks (max {} chars/chunk)",
                meta.getBrand(), meta.getModel(), chunks.size(), MAX_CHUNK_CHARS);
        for (int i = 0; i < chunks.size(); i++) {
            String firstLine = chunks.get(i).lines()
                    .filter(l -> l.startsWith("##"))
                    .findFirst().orElse("(no heading)");
            log.debug("  chunk {}: {} ({} chars)", i, firstLine, chunks.get(i).length());
        }
    }
}
