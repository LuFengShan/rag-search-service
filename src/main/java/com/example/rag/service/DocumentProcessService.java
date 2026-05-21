package com.example.rag.service;

import com.example.rag.entity.Document;
import com.example.rag.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档异步处理服务
 * <p>
 * 负责将上传的文档文件解析、分块、向量化并存入数据库。
 * 整个流程在独立线程池中异步执行，不阻塞用户的 HTTP 请求。
 *
 * <h3>处理流程概览</h3>
 * <pre>
 * 用户上传文件
 *   └── DocumentService.uploadDocument()
 *        ├── ① 保存文件到磁盘
 *        ├── ② 插入 document 记录（状态 = UPLOADING）
 *        └── ③ 调用本服务 processDocument() ──异步──→ ④ 解析 → ⑤ 分块 → ⑥ 向量化 → ⑦ 状态更新
 * </pre>
 *
 * <h3>状态机</h3>
 * UPLOADING → PROCESSING → INDEXED（成功）
 *                        → FAILED（失败/无内容）
 *
 * @see DocumentService 文档上传入口
 * @see VectorService 向量生成与检索
 * @see DocumentParserService 文档内容提取（Tika/ZIP 回退）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessService {

    private final DocumentMapper documentMapper;

    private final VectorService vectorService;

    private final DocumentParserService documentParserService;

    /**
     * 异步处理文档：解析内容 → 智能分块 → 向量化并入库
     * <p>
     * 通过 {@code @Async("documentProcessExecutor")} 注解，
     * 该方法会在名为 "documentProcessExecutor" 的线程池中执行，
     * 不会阻塞调用方（HTTP 请求线程）。
     * <p>
     * 线程池配置见 {@code com.example.rag.config.AsyncConfig}
     *
     * @param document 已入库的文档实体，包含文件路径、标题等信息
     */
    @Async("documentProcessExecutor")
    public void processDocument(Document document) {
        // ===== 阶段 1：标记文档为「处理中」 =====
        // 先更新状态，让前端可以通过轮询 GET /api/documents/{id} 看到进度
        document.setStatus(Document.Status.PROCESSING);
        documentMapper.updateById(document);

        try {
            // ===== 阶段 2：解析文档内容 =====
            // DocumentParserService 内部会：
            //   1. 纯文本文件（.txt/.md 等）→ 直接读取
            //   2. 其他格式 → Tika 自动检测解析
            //   3. Tika 失败时 → ZIP 回退（直接提取归档中的 HTML/XML/TXT）
            String content = documentParserService.parseDocument(
                    document.getFilePath(),
                    document.getTitle());

            // 解析结果为空 → 标记失败，直接返回
            if (content == null || content.trim().isEmpty()) {
                log.warn("Document has no extractable content: {}", document.getId());
                document.setStatus(Document.Status.FAILED);
                documentMapper.updateById(document);
                return;
            }

            // ===== 阶段 3：智能分块（Chunking） =====
            // 将长文本按段落切分成大小适中的片段，每个片段作为一个检索单元
            List<String> chunks = smartChunkContent(content);

            // ===== 阶段 4：向量化并入库 =====
            // 遍历每个分块，调用 Embedding 模型生成向量，存入 pgvector
            int chunkIndex = 0;
            for (String chunk : chunks) {
                if (!chunk.trim().isEmpty()) {
                    // embed() 内部：
                    //   优先用阿里云 DashScope text-embedding-v4
                    //   API 不可用时降级为基于 hash 的 mock 向量
                    float[] embedding = vectorService.embed(chunk);

                    // 将分块内容和向量一起写入 document_chunk_vector 表
                    vectorService.saveDocumentChunk(document.getId(), chunkIndex, chunk, embedding);
                    chunkIndex++;
                }
            }

            log.info("Document processed: {} chunks created for document {}", chunkIndex, document.getId());

            // ===== 阶段 5：标记完成 =====
            // INDEXED 状态表示文档已经可以被检索和问答了
            document.setStatus(Document.Status.INDEXED);
            documentMapper.updateById(document);

        } catch (Exception e) {
            // 任何阶段出现异常都标记为 FAILED，不抛出（避免影响调用方）
            log.error("Failed to process document: {}", document.getId(), e);
            document.setStatus(Document.Status.FAILED);
            documentMapper.updateById(document);
        }
    }

    /**
     * 智能文本分块（Chunking）
     * <p>
     * 将长文档按段落切分成大小合适的片段。目标是每个 chunk 足够自包含
     * 且长度适中（50~500 字符），以便嵌入为向量后进行语义检索时能得到
     * 高质量的匹配结果。
     *
     * <h3>分块策略（按段落处理）</h3>
     * <ol>
     *   <li>用连续空行（2个以上换行）作为段落分割符</li>
     *   <li><b>适中段落（50~500 字符）</b>：直接作为一个 chunk</li>
     *   <li><b>短段落（&lt;50 字符）</b>：尝试合并到前一个 chunk
     *       <ul>
     *         <li>合并后 ≤500 → 合并成功</li>
     *         <li>合并后 &gt;500 → 分别独立保存</li>
     *         <li>没有前一个 chunk → 单独作为一个 chunk</li>
     *       </ul>
     *   </li>
     *   <li><b>长段落（&gt;500 字符）</b>：先按句子拆分，再按 ≤500 聚合成 chunk</li>
     * </ol>
     *
     * @param content 文档清洗后的全文
     * @return 分块后的文本列表，每个元素 ≤500 字符
     */
    private List<String> smartChunkContent(String content) {
        List<String> chunks = new ArrayList<>();

        // \\n{2,} 匹配 2 个或更多连续换行符 → 以"段落间空行"作为分割边界
        // 例如："第一段\n\n\n第二段" 会被切成 ["第一段", "第二段"]
        String[] paragraphs = content.split("\\n{2,}");

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) {
                continue; // 跳过全空白的"段落"
            }

            // ---- 情况 A：段落长度刚好合适（50 ~ 500 字符） ----
            if (paragraph.length() >= 50 && paragraph.length() <= 500) {
                chunks.add(paragraph);

            // ---- 情况 B：段落偏短（< 50 字符，如标题、单行） ----
            } else if (paragraph.length() < 50) {
                if (!chunks.isEmpty()) {
                    // 尝试和上一个 chunk 合并
                    String lastChunk = chunks.remove(chunks.size() - 1);
                    String merged = lastChunk + "\n\n" + paragraph;
                    if (merged.length() <= 500) {
                        // 合并后长度 OK → 放回去
                        chunks.add(merged);
                    } else {
                        // 合并后超长 → 各自独立
                        chunks.add(lastChunk);
                        chunks.add(paragraph);
                    }
                } else {
                    // 还没有任何 chunk → 直接添加，即使很短
                    chunks.add(paragraph);
                }

            // ---- 情况 C：段落太长（> 500 字符） ----
            } else {
                // 第一步：把长段落按标点符号拆分成句子
                List<String> sentences = splitIntoSentences(paragraph);

                // 第二步：按 500 字符上限将句子聚合成 chunk
                StringBuilder currentChunk = new StringBuilder();
                for (String sentence : sentences) {
                    // 如果当前 chunk 加上新句子会超长，先保存当前 chunk
                    if (currentChunk.length() + sentence.length() > 500 && currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                        currentChunk = new StringBuilder();
                    }
                    currentChunk.append(sentence).append(" ");
                }

                // 别漏了最后一段
                if (currentChunk.length() > 0) {
                    String finalChunk = currentChunk.toString().trim();
                    if (!finalChunk.isEmpty()) {
                        chunks.add(finalChunk);
                    }
                }
            }
        }

        return chunks;
    }

    /**
     * 按句子边界拆分文本
     * <p>
     * 使用正则正向回顾断言 {@code (?<=[。！？.!?])}，
     * 在中英文标点符号之后切分文本，保留标点符号在句子末尾。
     *
     * <h3>示例</h3>
     * <pre>
     * 输入："你好。世界！测试文本"
     * 输出：["你好。", "世界！", "测试文本"]
     * </pre>
     *
     * <h3>降级策略</h3>
     * 如果文本中没有任何标点符号（如纯英文无标点的长字符串），
     * 按每 200 字符一刀切的方式降级处理。
     *
     * @param text 待拆分的文本段落
     * @return 按句子边界切分后的列表
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();

        // (?<=...) 是「正向回顾」（positive lookbehind）
        // 含义：在 [。！？.!?] 这些字符「之后」的位置切分
        // \\s* 匹配切分后开头可能残留的空白字符
        // 这样每个句子末尾会保留标点符号，语义更完整
        String[] parts = text.split("(?<=[。！？.!?])\\s*");

        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                sentences.add(part);
            }
        }

        // 如果按标点切分后一个句子都没产生（纯无标点长文本）
        // 降级为固定长度切分
        if (sentences.isEmpty()) {
            int chunkSize = 200;
            for (int i = 0; i < text.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, text.length());
                String chunk = text.substring(i, end).trim();
                if (!chunk.isEmpty()) {
                    sentences.add(chunk);
                }
            }
        }

        return sentences;
    }
}
