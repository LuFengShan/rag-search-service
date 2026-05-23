package com.example.rag.mcp;

import com.example.rag.tool.AutomotiveAssistantTools;
import com.example.rag.tool.CarPriceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP 工具适配器
 * <p>
 * 使用 Spring AI 1.1.6 的 @McpTool 注解，将汽车销售助手工具暴露为 MCP 服务。
 * 任何 MCP 兼容的 AI 客户端（如 Claude Desktop、Cursor、VS Code Copilot 等）
 * 都可以通过 MCP 协议发现并调用这些工具。
 * </p>
 *
 * <h3>MCP 协议用途</h3>
 * MCP (Model Context Protocol) 让外部 AI 应用可以：
 * <ul>
 *   <li>自动发现此服务提供的工具列表</li>
 *   <li>调用工具获取实时数据（价格、优惠、联系方式等）</li>
 *   <li>集成到更广泛的 AI 生态系统中</li>
 * </ul>
 *
 * <h3>传输方式</h3>
 * 使用 Streamable-HTTP 协议，端点：{@code /mcp}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolsAdapter {

    private final CarPriceCalculator priceCalculator;
    private final AutomotiveAssistantTools assistantTools;

    // ==================== 价格计算工具 ====================

    @McpTool(name = "calculate-on-road-price",
            description = "计算某款车型所有配置的落地价格，包含购置税、保险、上牌费等。" +
                    "调用时机：用户询问某车型落地价、上路价、全款总价时调用")
    public String calculateOnRoadPrice(
            @McpToolParam(description = "车型名称，如秦PLUS、凯美瑞、汉兰达等") String carModel) {
        log.info("MCP: calculateOnRoadPrice called for model={}", carModel);
        return priceCalculator.calculateOnRoadPrice(carModel);
    }

    @McpTool(name = "calculate-monthly-payment",
            description = "计算分期购车的月供金额，含不同首付比例和贷款年限的组合方案。" +
                    "调用时机：用户询问月供、分期、按揭方案时调用")
    public String calculateMonthlyPayment(
            @McpToolParam(description = "车型名称") String carModel,
            @McpToolParam(description = "首付比例，如0.3表示30%首付") double downPaymentRatio,
            @McpToolParam(description = "贷款年限（1-5年）") int years) {
        log.info("MCP: calculateMonthlyPayment called for model={}, ratio={}, years={}",
                carModel, downPaymentRatio, years);
        return priceCalculator.calculateMonthlyPayment(carModel, downPaymentRatio, years);
    }

    // ==================== 助手服务工具 ====================

    @McpTool(name = "get-contact-info",
            description = "获取联系方式、门店地址、试驾预约、售后热线等信息。" +
                    "调用时机：用户询问怎么联系、电话、地址、门店、试驾、售后时调用")
    public String getContactInfo(
            @McpToolParam(description = "咨询类型：sales(销售)、afterSales(售后)、testDrive(试驾)，不明确时传sales",
                    required = false)
            String type) {
        log.info("MCP: getContactInfo called for type={}", type);
        return assistantTools.getContactInfo(type);
    }

    @McpTool(name = "get-promotions",
            description = "查询当前的优惠活动、促销、降价、金融方案、置换补贴、购置税政策等。" +
                    "调用时机：用户询问优惠、活动、贷款方案、置换补贴时调用")
    public String getPromotions(
            @McpToolParam(description = "咨询类型：loan(贷款/首付/月供)、tradeIn(置换补贴)、general(一般活动)",
                    required = false)
            String category) {
        log.info("MCP: getPromotions called for category={}", category);
        return assistantTools.getPromotions(category);
    }

    @McpTool(name = "greet",
            description = "回答问候语或能力咨询，介绍助手能提供的服务。" +
                    "调用时机：用户打招呼、询问助手能力时调用")
    public String greet() {
        log.info("MCP: greet called");
        return assistantTools.greet();
    }
}
