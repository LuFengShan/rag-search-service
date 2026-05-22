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
import java.util.stream.Collectors;

/**
 * 代理服务（核心RAG逻辑）
 * <p>
 * 负责处理用户提问，执行检索增强生成（RAG）流程，是系统的核心组件。
 * 主要功能：
 * <ul>
 *   <li>解析用户问题，判断是否需要调用工具</li>
 *   <li>执行向量检索，获取相关文档片段</li>
 *   <li>调用 LLM 生成答案</li>
 *   <li>支持多轮对话上下文</li>
 * </ul>
 *
 * <h3>RAG 处理流程</h3>
 * <pre>
 * 用户问题 → 工具调用判断 → 向量检索 → 构建上下文 → LLM 生成 → 返回答案
 *              ↓                              ↓
 *         价格计算器等                    会话历史
 * </pre>
 *
 * <h3>系统提示词选择</h3>
 * 根据知识库配置选择不同的系统提示词：
 * <ul>
 *   <li>CAR_MD 类型：汽车销售顾问角色</li>
 *   <li>其他类型：企业知识库助手角色</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    /** LLM 聊天模型（DeepSeek） */
    private final ChatModel chatModel;

    /** 向量服务 */
    private final VectorService vectorService;

    /** 问题数据访问层 */
    private final QuestionMapper questionMapper;

    /** 答案数据访问层 */
    private final AnswerMapper answerMapper;

    /** 知识库数据访问层 */
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    /** 技能分类器 */
    private final SkillClassifier skillClassifier;

    /** 汽车价格计算器 */
    private final CarPriceCalculator carPriceCalculator;

    /** JSON序列化工具 */
    private final ObjectMapper objectMapper;

    /** 默认系统提示词（企业知识库助手） */
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

    /** 汽车销售场景系统提示词 */
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

    /**
     * 回答用户问题（无会话上下文）
     *
     * @param question 用户问题
     * @param knowledgeBaseId 知识库ID
     * @return 答案响应对象
     */
    public AnswerResponse answer(String question, UUID knowledgeBaseId) {
        return answer(question, knowledgeBaseId, null);
    }

    /**
     * 回答用户问题（支持会话上下文）
     * <p>
     * 执行步骤：
     * <ol>
     *   <li>判断是否需要调用工具（价格计算等）</li>
     *   <li>尝试技能匹配（问候、联系等）</li>
     *   <li>执行向量检索获取相关文档</li>
     *   <li>构建提示词（包含上下文和历史对话）</li>
     *   <li>调用 LLM 生成答案</li>
     * </ol>
     *
     * @param question 用户问题
     * @param knowledgeBaseId 知识库ID（可选）
     * @param sessionId 会话ID（可选）
     * @return 答案响应对象
     */
    public AnswerResponse answer(String question, UUID knowledgeBaseId, UUID sessionId) {
        // 步骤1：判断是否需要调用工具
        String toolResult = callToolsIfNeeded(question);
        if (toolResult != null && !toolResult.isEmpty()) {
            // 如果有工具调用结果，构建包含工具结果的提示词
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

        // 步骤2：尝试技能匹配（问候、联系等）
        AnswerResponse skillResponse = skillClassifier.matchAndExecute(question);
        if (skillResponse != null) {
            return skillResponse;
        }

        // 步骤3：执行向量检索
        String ragContext = buildRagContext(question, knowledgeBaseId, sessionId);

        // 步骤4：构建消息列表
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(selectSystemPrompt(knowledgeBaseId)));

        // 添加会话历史（如果有）
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

        // 添加当前问题
        messages.add(new UserMessage(
                "【检索到的相关文档内容】\n\n" + ragContext + "\n【用户问题】\n" + question + "\n\n请基于以上文档内容回答用户的问题。"));

        // 步骤5：调用 LLM
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

    /**
     * 判断是否需要调用工具并执行
     * <p>
     * 支持的工具调用场景：
     * <ul>
     *   <li>价格计算：匹配"落地多少钱"、"落地价"等关键词 + 车型名称</li>
     *   <li>月供计算：匹配"月供"、"分期"等关键词 + 首付比例 + 贷款年限</li>
     * </ul>
     *
     * @param question 用户问题
     * @return 工具执行结果，如果不需要调用工具则返回 null
     */
    private String callToolsIfNeeded(String question) {
        if (question == null) return null;
        String q = question.trim();

        // 落地价计算
        if (q.matches(".*(落地多少钱|落地价|落地价格|上路多少钱|全办下来|全款.*多少|落地要多少).*")
                && q.matches(".*(秦|汉|唐|宋|海豹|海鸥|元|卡罗拉|凯美瑞|RAV4|汉兰达|亚洲龙|普拉多|思域|雅阁|CR-V|飞度|型格|哈弗|坦克|蓝山|好猫).*")) {
            String model = extractCarModel(q);
            return carPriceCalculator.calculateOnRoadPrice(model);
        }

        // 月供计算
        if (q.matches(".*(月供|分期|按揭|贷款.*月供|首付.*月供|分.*年.*月供).*")) {
            String model = extractCarModel(q);
            double ratio = 0.3; // 默认首付比例
            int years = 3; // 默认贷款年限

            // 解析首付比例
            if (q.contains("20%") || q.contains("两成")) ratio = 0.2;
            else if (q.contains("30%") || q.contains("三成")) ratio = 0.3;
            else if (q.contains("50%") || q.contains("五成")) ratio = 0.5;
            else if (q.contains("0首付") || q.contains("零首付")) ratio = 0.0;

            // 解析贷款年限
            java.util.regex.Matcher ym = java.util.regex.Pattern.compile("(\\d+)\\s*年").matcher(q);
            if (ym.find()) years = Integer.parseInt(ym.group(1));

            return carPriceCalculator.calculateMonthlyPayment(model, ratio, years);
        }

        return null;
    }

    /**
     * 从问题中提取车型名称
     * <p>
     * 支持的车型列表：比亚迪、丰田、本田、长城等主流车型。
     *
     * @param question 用户问题
     * @return 车型名称，如果未匹配到则返回原问题
     */
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

    /**
     * 构建 RAG 上下文
     * <p>
     * 执行向量检索，获取与问题最相关的文档片段。
     *
     * @param question 用户问题
     * @param knowledgeBaseId 知识库ID（可选）
     * @param sessionId 会话ID（可选）
     * @return 检索到的文档内容拼接字符串
     */
    private String buildRagContext(String question, UUID knowledgeBaseId, UUID sessionId) {
        // 生成问题的向量表示
        float[] questionEmbedding = vectorService.embed(question);
        // 执行余弦相似度检索
        List<DocumentChunk> similarChunks = vectorService.searchByCosineSimilarity(
                questionEmbedding, knowledgeBaseId, 5);

        // 如果没有找到相关文档
        if (similarChunks.isEmpty()) return "（未找到相关文档内容）";

        // 拼接文档内容
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < similarChunks.size(); i++) {
            sb.append("文档片段").append(i + 1).append("：\n");
            sb.append(similarChunks.get(i).getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 选择系统提示词
     * <p>
     * 根据知识库配置选择合适的系统提示词。
     *
     * @param knowledgeBaseId 知识库ID（可选）
     * @return 系统提示词
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
     * 计算答案置信度
     *
     * @param chunks 检索到的文档分块
     * @return 置信度（0.3 ~ 1.0）
     */
    private float calculateConfidence(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return 0.3f;
        int count = Math.min(chunks.size(), 5);
        return 0.5f + count * 0.1f;
    }

    /**
     * 降级答案生成
     * <p>
     * 当 LLM 调用失败时使用的降级方案。
     *
     * @param question 用户问题
     * @param sources 来源信息
     * @return 降级答案响应
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