package com.example.rag.service;

import com.example.rag.entity.Document;
import com.example.rag.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessService {

    private final DocumentMapper documentMapper;

    private final VectorService vectorService;

    private final DocumentParserService documentParserService;

    @Async("documentProcessExecutor")
    public void processDocument(Document document) {
        document.setStatus(Document.Status.PROCESSING);
        documentMapper.updateById(document);

        try {
            String content = documentParserService.parseDocument(
                    document.getFilePath(),
                    document.getTitle());

            if (content == null || content.trim().isEmpty()) {
                log.warn("Document has no extractable content: {}", document.getId());
                document.setStatus(Document.Status.FAILED);
                documentMapper.updateById(document);
                return;
            }

            List<String> chunks = smartChunkContent(content);
            int chunkIndex = 0;
            for (String chunk : chunks) {
                if (!chunk.trim().isEmpty()) {
                    float[] embedding = vectorService.generateMockEmbedding(chunk);
                    vectorService.saveDocumentChunk(document.getId(), chunkIndex, chunk, embedding);
                    chunkIndex++;
                }
            }

            log.info("Document processed: {} chunks created for document {}", chunkIndex, document.getId());

            document.setStatus(Document.Status.INDEXED);
            documentMapper.updateById(document);
        } catch (Exception e) {
            log.error("Failed to process document: {}", document.getId(), e);
            document.setStatus(Document.Status.FAILED);
            documentMapper.updateById(document);
        }
    }

    private List<String> smartChunkContent(String content) {
        List<String> chunks = new ArrayList<>();

        String[] paragraphs = content.split("\\n{2,}");

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) {
                continue;
            }

            if (paragraph.length() >= 50 && paragraph.length() <= 500) {
                chunks.add(paragraph);
            } else if (paragraph.length() < 50) {
                if (!chunks.isEmpty()) {
                    String lastChunk = chunks.remove(chunks.size() - 1);
                    String merged = lastChunk + "\n\n" + paragraph;
                    if (merged.length() <= 500) {
                        chunks.add(merged);
                    } else {
                        chunks.add(lastChunk);
                        chunks.add(paragraph);
                    }
                } else {
                    chunks.add(paragraph);
                }
            } else {
                List<String> sentences = splitIntoSentences(paragraph);
                StringBuilder currentChunk = new StringBuilder();

                for (String sentence : sentences) {
                    if (currentChunk.length() + sentence.length() > 500 && currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                        currentChunk = new StringBuilder();
                    }
                    currentChunk.append(sentence).append(" ");
                }

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

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();

        String[] parts = text.split("(?<=[。！？.!?])\\s*");

        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                sentences.add(part);
            }
        }

        if (sentences.isEmpty()) {
            int chunkSize = 200;
            for (int i = 0; i < text.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, text.length());
                String chunk = text.substring(i, end);
                if (!chunk.trim().isEmpty()) {
                    sentences.add(chunk);
                }
            }
        }

        return sentences;
    }
}
