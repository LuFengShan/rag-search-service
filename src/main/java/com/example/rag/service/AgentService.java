package com.example.rag.service;

import com.example.rag.document.CarDocumentMetadata;
import com.example.rag.document.MarkdownFrontmatterExtractor;
import com.example.rag.dto.response.AnswerResponse;
import com.example.rag.entity.Answer;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.entity.KnowledgeBase;
import com.example.rag.entity.Question;
import com.example.rag.mapper.AnswerMapper;
import com.example.rag.mapper.KnowledgeBaseMapper;
import com.example.rag.mapper.QuestionMapper;
import com.example.rag.skill.SkillClassifier;
import com.example.rag.tool.CarPriceCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatModel chatModel;
    private final VectorService vectorService;
    private final QuestionMapper questionMapper;
    private final AnswerMapper answerMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final SkillClassifier skillClassifier;
    private final CarPriceCalculator carPriceCalculator;
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

    public AnswerResponse answer(String question, UUID knowledgeBaseId, UUID sessionId) {
        String toolResult = callToolsIfNeeded(question);
        if (toolResult != null && !toolResult.isEmpty()) {
            String context = buildRagContext(question, knowledgeBaseId, sessionId);
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(selectSystemPrompt(knowledgeBaseId)));
            messages.add(new UserMessage("【工具计算结果】\n" + toolResult + "\n\n" +
                    "【检索到的相关文档内容】\n\n" + context + "\n" +
                    "【用户问题】\n" + question + "\n\n" +
                    "请结合工具计算的具体价格数据和文档内容，给用户一个专业、友好的回答。"));

            try {
                String answer = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
                return AnswerResponse.builder().answer(answer).sources(List.of()).confidence(0.9f).build();
            } catch (Exception e) {
                log.error("LLM call failed, returning tool result directly", e);
                return AnswerResponse.builder().answer(toolResult).sources(List.of()).confidence(1.0f).build();
            }
        }

        AnswerResponse skillResponse = skillClassifier.matchAndExecute(question);
        if (skillResponse != null) {
            return skillResponse;
        }

        String ragContext = buildRagContext(question, knowledgeBaseId, sessionId);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(selectSystemPrompt(knowledgeBaseId)));

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

        messages.add(new UserMessage(
                "【检索到的相关文档内容】\n\n" + ragContext + "\n【用户问题】\n" + question + "\n\n请基于以上文档内容回答用户的问题。"));

        Prompt prompt = new Prompt(messages);

        try {
            String answer = chatModel.call(prompt).getResult().getOutput().getText();
            log.info("Agent generated answer, session={}", sessionId);
            return AnswerResponse.builder()
                    .answer(answer)
                    .sources(List.of())
                    .confidence(0.8f)
                    .build();
        } catch (Exception e) {
            log.error("DeepSeek API call failed, falling back to rule-based answer", e);
            return fallbackAnswer(question, List.of());
        }
    }

    private String callToolsIfNeeded(String question) {
        if (question == null) return null;
        String q = question.trim();

        if (q.matches(".*(落地多少钱|落地价|落地价格|上路多少钱|全办下来|全款.*多少|落地要多少).*")
                && q.matches(".*(秦|汉|唐|宋|海豹|海鸥|元|卡罗拉|凯美瑞|RAV4|汉兰达|亚洲龙|普拉多|思域|雅阁|CR-V|飞度|型格|哈弗|坦克|蓝山|好猫).*")) {
            String model = extractCarModel(q);
            return carPriceCalculator.calculateOnRoadPrice(model);
        }

        if (q.matches(".*(月供|分期|按揭|贷款.*月供|首付.*月供|分.*年.*月供).*")) {
            String model = extractCarModel(q);
            double ratio = 0.3;
            int years = 3;
            if (q.contains("20%") || q.contains("两成")) ratio = 0.2;
            else if (q.contains("30%") || q.contains("三成")) ratio = 0.3;
            else if (q.contains("50%") || q.contains("五成")) ratio = 0.5;
            else if (q.contains("0首付") || q.contains("零首付")) ratio = 0.0;

            java.util.regex.Matcher ym = java.util.regex.Pattern.compile("(\\d+)\\s*年").matcher(q);
            if (ym.find()) years = Integer.parseInt(ym.group(1));

            return carPriceCalculator.calculateMonthlyPayment(model, ratio, years);
        }

        return null;
    }

    private String extractCarModel(String question) {
        String[] models = {"秦PLUS", "汉 EV", "汉 DM-i", "汉", "唐 EV", "唐 DM-i", "唐",
                "宋PLUS", "海豹", "海鸥", "元PLUS", "卡罗拉双擎", "卡罗拉",
                "凯美瑞双擎", "凯美瑞", "RAV4荣放", "RAV4双擎", "RAV4",
                "汉兰达", "亚洲龙双擎", "亚洲龙", "普拉多", "思域混动", "思域",
                "雅阁混动", "雅阁", "CR-V混动", "CR-V", "飞度", "型格混动", "型格",
                "哈弗H6混动", "哈弗H6", "坦克300", "蓝山", "欧拉好猫"};
        for (String model : models) {
            if (question.contains(model)) return model;
        }
        return question;
    }

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

    private String selectSystemPrompt(UUID knowledgeBaseId) {
        if (knowledgeBaseId != null) {
            KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
            if (kb != null && kb.getConfig() != null && kb.getConfig().contains("\"docType\":\"CAR_MD\"")) {
                return CAR_SALES_SYSTEM_PROMPT;
            }
        }
        return DEFAULT_SYSTEM_PROMPT;
    }

    private float calculateConfidence(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return 0.3f;
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
        return AnswerResponse.builder().answer(answer).sources(sources).confidence(0.5f).build();
    }
}
