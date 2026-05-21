package com.example.rag.service;

import com.example.rag.dto.response.AnswerResponse;
import com.example.rag.entity.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatModel chatModel;

    private final VectorService vectorService;

    private static final String SYSTEM_PROMPT = """
            你是一个企业知识库智能助手。请根据以下检索到的文档内容回答用户的问题。

            要求：
            1. 只使用提供的文档内容回答，不要编造信息
            2. 如果文档内容不足以回答用户的问题，请明确告知
            3. 回答要简洁、准确、有条理
            4. 引用具体文档内容时请说明来源
            5. 使用中文回答
            """;

    public AnswerResponse answer(String question, UUID knowledgeBaseId) {
        float[] questionEmbedding = vectorService.embed(question);

        List<DocumentChunk> similarChunks = vectorService.searchByCosineSimilarity(
                questionEmbedding, knowledgeBaseId, 5);

        List<AnswerResponse.SourceInfo> sources = buildSources(similarChunks);

        String context = buildContext(similarChunks);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage("【检索到的相关文档内容】\n\n" + context + "\n【用户问题】\n" + question + "\n\n请基于以上文档内容回答用户的问题。")
        ));

        try {
            String answer = chatModel.call(prompt).getResult().getOutput().getText();
            log.info("Agent generated answer for question, {} sources used", sources.size());
            return AnswerResponse.builder()
                    .answer(answer)
                    .sources(sources)
                    .confidence(calculateConfidence(similarChunks))
                    .build();
        } catch (Exception e) {
            log.error("DeepSeek API call failed, falling back to rule-based answer", e);
            return fallbackAnswer(question, sources);
        }
    }

    private String buildContext(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) {
            return "（未找到相关文档内容）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("文档片段").append(i + 1).append("：\n");
            sb.append(chunks.get(i).getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private List<AnswerResponse.SourceInfo> buildSources(List<DocumentChunk> chunks) {
        List<AnswerResponse.SourceInfo> sources = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            sources.add(AnswerResponse.SourceInfo.builder()
                    .documentId(chunk.getDocumentId())
                    .documentTitle("知识库文档")
                    .chunkContent(chunk.getContent().length() > 200
                            ? chunk.getContent().substring(0, 200) + "..." : chunk.getContent())
                    .confidence(0.85f)
                    .build());
        }
        return sources;
    }

    private float calculateConfidence(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) {
            return 0.3f;
        }
        int count = Math.min(chunks.size(), 5);
        return 0.5f + count * 0.1f;
    }

    private AnswerResponse fallbackAnswer(String question, List<AnswerResponse.SourceInfo> sources) {
        String answer = "根据知识库中的文档内容，以下是相关信息：\n\n";
        if (!sources.isEmpty()) {
            answer += sources.stream()
                    .map(s -> "📄 " + s.getChunkContent())
                    .collect(Collectors.joining("\n\n"));
        } else {
            answer += "未找到与您问题直接相关的文档内容，建议您重新表述问题或查看相关文档。";
        }
        return AnswerResponse.builder()
                .answer(answer)
                .sources(sources)
                .confidence(0.5f)
                .build();
    }
}
