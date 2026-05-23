package com.example.rag.service;

import com.example.rag.dto.response.AnswerResponse;
import com.example.rag.entity.Answer;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.entity.KnowledgeBase;
import com.example.rag.entity.Question;
import com.example.rag.mapper.AnswerMapper;
import com.example.rag.mapper.KnowledgeBaseMapper;
import com.example.rag.mapper.QuestionMapper;
import com.example.rag.tool.AutomotiveAssistantTools;
import com.example.rag.tool.CarPriceCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 代理服务（核心RAG逻辑）
 * <p>
 * 使用 Spring AI 1.1.6 的 ChatClient.tools() API，让 LLM 自主决策工具调用。
 * 替代了旧版的手动正则匹配（callToolsIfNeeded）和自定义技能分类器（SkillClassifier）。
 * </p>
 *
 * <h3>新的 RAG + Tool Calling 流程</h3>
 * <pre>
 * 用户问题 → 向量检索(RAG) → 构建消息(Sys+History+RAG+Q) → ChatClient.tools(...) → LLM自主决策 → 返回答案
 *                                                                   ↓
 *                                              LLM 自动调用 CarPriceCalculator /
 *                                              AutomotiveAssistantTools / 直接回答
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatModel chatModel;
    private final VectorService vectorService;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final CarPriceCalculator carPriceCalculator;
    private final AutomotiveAssistantTools automotiveTools;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个企业知识库智能助手。请根据以下检索到的文档内容，结合对话历史，回答用户的问题。

            要求：
            1. 只使用提供的文档内容回答，不要编造信息
            2. 如果文档内容不足以回答用户的问题，请明确告知
            3. 回答要简洁、准确、有条理
            4. 引用具体文档内容时请说明来源
            5. 使用中文回答
            6. 如果用户的追问涉及之前对话历史中已经讨论过的内容，请基于历史上下文进行回答
            """;

    private static final String CAR_SALES_SYSTEM_PROMPT = """
            你是一个专业的汽车销售顾问AI助手，正在辅助直播间销售场景。
            你的任务是：根据知识库中的车型文档，专业、热情地回答用户关于汽车的各种问题。

            ## 可用工具
            你可以调用可用的工具函数来获取实时数据（如价格计算、月供计算、优惠活动、联系方式等）。
            当用户询问价格、月供、优惠、活动、联系方式等问题时，优先调用对应工具获取准确数据。

            ## 你的能力范围：
            1. **车型咨询**：介绍具体车型的品牌、配置、参数、亮点
            2. **价格咨询**：提供价格区间、不同配置的价格差异
            3. **车型对比**：对比不同车型的优劣，帮用户决策
            4. **预算推荐**：根据用户的预算范围，推荐合适的车型
            5. **适用场景分析**：分析车型适合的人群和使用场景

            ## 回答要求：
            1. 只使用提供的文档内容回答，绝不编造不存在的信息
            2. 如果文档中找不到相关信息，诚实告知用户"这方面信息暂时没有，建议咨询线下门店"
            3. 回答要有条理，使用分点列举，方便直播间用户快速获取信息
            4. 语气亲切热情，像真人销售顾问一样，适当使用"咱们"、"推荐"等口语化表达
            5. 价格务必精确，不要模糊；有多个配置时要分别说明
            6. 对比车型时，列出关键差异点（价格、油耗、空间、配置等）
            7. 如果用户预算不明确，主动追问预算范围以便精准推荐
            8. 使用中文回答
            """;

    public AnswerResponse answer(String question, UUID knowledgeBaseId) {
        return answer(question, knowledgeBaseId, null);
    }

    /**
     * 回答用户问题（支持会话上下文）
     * <p>
     * 使用 ChatClient.tools() 让 LLM 自主决策是否调用工具。
     * 不再需要手动正则匹配或技能分类器。
     * </p>
     *
     * @param question 用户问题
     * @param knowledgeBaseId 知识库ID（可选）
     * @param sessionId 会话ID（可选）
     * @return 答案响应对象
     */
    public AnswerResponse answer(String question, UUID knowledgeBaseId, UUID sessionId) {
        // 执行向量检索获取 RAG 上下文
        String ragContext = buildRagContext(question, knowledgeBaseId, sessionId);

        // 构建消息列表
        List<Message> messages = buildMessages(question, ragContext, knowledgeBaseId, sessionId);

        // 使用 ChatClient + tools 让 LLM 自主决策工具调用
        String answer = callChatClientWithTools(messages);

        if (answer != null) {
            log.info("Agent generated answer, session={}", sessionId);
            return AnswerResponse.builder()
                    .answer(answer)
                    .sources(List.of())
                    .confidence(0.8f)
                    .build();
        } else {
            log.warn("LLM call failed after retries, falling back to rule-based answer");
            return fallbackAnswer(question, List.of());
        }
    }

    /**
     * 构建消息列表
     * <p>
     * 包含：系统提示词 + 会话历史 + RAG 上下文 + 当前问题
     * </p>
     */
    private List<Message> buildMessages(String question, String ragContext,
                                        UUID knowledgeBaseId, UUID sessionId) {
        List<Message> messages = new ArrayList<>();

        // 系统提示词
        messages.add(new SystemMessage(selectSystemPrompt(knowledgeBaseId)));

        // 会话历史
        if (sessionId != null) {
            List<Question> history = questionMapper.findBySessionIdOrderByCreatedAt(sessionId);
            for (Question q : history) {
                Answer a = answerMapper.findByQuestionId(q.getId()).orElse(null);
                messages.add(new UserMessage(q.getQuestion()));
                if (a != null && a.getAnswer() != null) {
                    messages.add(new AssistantMessage(a.getAnswer()));
                }
            }
        }

        // RAG 上下文 + 用户问题
        messages.add(new UserMessage(
                "【检索到的相关文档内容】\n\n" + ragContext + "\n【用户问题】\n" + question +
                "\n\n请基于以上文档内容回答用户的问题。"));

        return messages;
    }

    /**
     * 使用 ChatClient + tools 调用 LLM（带重试）
     * <p>
     * 将所有 @Tool 工具注入 ChatClient，LLM 会自动决策何时调用哪个工具。
     * ChatClient 自动执行工具调用并将结果反馈给 LLM 生成最终答案。
     * </p>
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String callChatClientWithTools(List<Message> messages) {
        return ChatClient.builder(chatModel).build()
                .prompt()
                .messages(messages)
                .tools(carPriceCalculator, automotiveTools)
                .call()
                .content();
    }

    @Recover
    public String callChatClientWithToolsFallback(Exception e, List<Message> messages) {
        log.warn("ChatClient+Tools call failed after retries: {}", e.getMessage());
        return null;
    }

    /**
     * 调用 LLM 模型（带重试，无工具调用的简单场景）
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String callChatModelWithRetry(List<Message> messages) {
        return ChatClient.builder(chatModel).build()
                .prompt()
                .messages(messages)
                .call()
                .content();
    }

    @Recover
    public String callChatModelWithRetryFallback(Exception e, List<Message> messages) {
        log.warn("ChatModel call failed after retries: {}", e.getMessage());
        return null;
    }

    /**
     * 构建 RAG 上下文
     * <p>
     * 执行向量检索，获取与问题最相关的文档片段。
     * </p>
     */
    private String buildRagContext(String question, UUID knowledgeBaseId, UUID sessionId) {
        float[] questionEmbedding = vectorService.embed(question);
        List<DocumentChunk> similarChunks = vectorService.searchByCosineSimilarity(
                questionEmbedding, knowledgeBaseId, 5);

        if (similarChunks.isEmpty()) return "（未找到相关文档内容）";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < similarChunks.size(); i++) {
            sb.append("文档片段").append(i + 1).append("：\n");
            sb.append(similarChunks.get(i).getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 选择系统提示词
     */
    private String selectSystemPrompt(UUID knowledgeBaseId) {
        if (knowledgeBaseId != null) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
            if (kb != null && kb.getConfig() != null && kb.getConfig().contains("\"docType\":\"CAR_MD\"")) {
                return CAR_SALES_SYSTEM_PROMPT;
            }
        }
        return DEFAULT_SYSTEM_PROMPT;
    }

    /**
     * 降级答案生成
     */
    private AnswerResponse fallbackAnswer(String question, List<AnswerResponse.SourceInfo> sources) {
        String answer = "根据知识库中的文档内容，以下是相关信息：\n\n";
        if (!sources.isEmpty()) {
            answer += sources.stream()
                    .map(s -> "📄 " + s.getChunkContent())
                    .collect(Collectors.joining("\n\n"));
        } else {
            answer += "未找到与您问题直接相关的文档内容，建议您重新表述问题或查看相关文档。";
        }
        return AnswerResponse.builder().answer(answer).sources(sources).confidence(0.5f).build();
    }
}
