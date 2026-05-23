package com.example.rag.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 汽车销售助手工具集
 * <p>
 * 使用 Spring AI 1.1.6 的 @Tool 注解，让 LLM 自主决策何时调用哪个工具。
 * 替代了旧的 AgentSkill 手动正则匹配方式。
 * </p>
 */
@Slf4j
@Component
public class AutomotiveAssistantTools {

    // ==================== 问候与能力介绍 ====================

    @Tool(description = "回答问候语或能力咨询，介绍助手能提供的服务")
    public String greet() {
        return """
                👋 您好！我是汽车销售顾问小助手～

                我可以帮您：
                🚗 **车型咨询**：介绍各品牌车型的配置、参数和亮点
                💰 **价格查询**：查询最新指导价和落地价参考
                ⚖️ **车型对比**：对比不同车型的优劣势，帮您决策
                🎯 **预算推荐**：根据预算范围，推荐最合适的车型
                📞 **联系方式**：需要线下看车随时问我""";
    }

    // ==================== 联系方式 ====================

    @Tool(description = "获取联系方式、门店地址、试驾预约、售后热线等信息。" +
            "调用时机：用户询问怎么联系、电话、地址、门店、试驾预约、售后、保养、维修联系方式时调用")
    public String getContactInfo(
            @ToolParam(description = "用户咨询的具体类型：sales(销售咨询)、afterSales(售后)、testDrive(试驾)，不明确时传null")
            String type) {

        if ("afterSales".equalsIgnoreCase(type)) {
            return """
                    🔧 **售后服务联系方式**

                    📞 售后热线：400-888-9999（24小时）
                    🏪 预约保养：打开微信小程序"XX汽车售后"即可预约
                    📍 最近服务中心：**XX区XX路XX号**（距您约3公里）

                    需要帮您直接预约吗？""";
        }

        if ("testDrive".equalsIgnoreCase(type)) {
            return """
                    🚗 **试驾预约**

                    📞 预约热线：400-123-8888（按1转试驾预约）
                    📱 线上预约：微信搜索"XX汽车"小程序 → 试驾预约
                    🏪 到店试驾：XX区汽车大道168号

                    到店试驾即送精美礼品一份 🎁""";
        }

        return """
                📞 **联系我们**

                🏪 **线下体验中心**：XX区汽车大道168号（地铁2号线"汽车城"站B出口）
                📱 **咨询热线**：400-123-8888
                💬 **微信客服**：搜索"XX汽车官方旗舰店"添加企业微信
                🕐 **营业时间**：周一至周日 9:00-21:00

                🚗 **试驾预约**：告诉我您想试驾的车型，我帮您安排！
                到店即送精美礼品一份 🎁""";
    }

    // ==================== 优惠活动 ====================

    @Tool(description = "查询当前的优惠活动、促销、降价、金融方案、置换补贴、购置税政策等")
    public String getPromotions(
            @ToolParam(description = "咨询类型：loan(贷款/首付/月供)、tradeIn(置换补贴)、general(一般活动)")
            String category) {

        if ("loan".equalsIgnoreCase(category)) {
            return """
                    🏦 **本月金融方案**

                    1. **0首付方案** — 指定车型享0首付，月供仅需1980元起
                    2. **3年0利率** — 首付30%以上，享受3年0利率金融补贴
                    3. **5年超长贷** — 月供低至1580元，轻松还款无压力

                    💡 举例：秦PLUS DM-i 优惠后7.98万
                    → 首付0元，分60期，月供约1680元
                    → 首付2.4万（30%），分36期0利率，月供约1550元

                    需要我帮您算具体车型的月供吗？""";
        }

        if ("tradeIn".equalsIgnoreCase(category)) {
            String deadline = LocalDate.now().plusMonths(1)
                    .format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
            return String.format("""
                    ♻️ **置换补贴政策（截至%s）**

                    1. 🚗 **厂家置换补贴**：旧车置换新车，最高补贴 **8000元**
                    2. 🏛️ **国家报废补贴**：报废国三及以下排放标准车辆，补贴 **2万元**
                    3. 🏙️ **地方消费券**：限时发放，最高可领 **5000元** 购车券

                    📋 置换所需材料：
                    - 旧车行驶证、登记证书
                    - 车主身份证
                    - 旧车交强险保单

                    💡 我们提供一站式置换服务，旧车评估→过户→提新车，最快当天搞定！""",
                    deadline);
        }

        return """
                🎉 **本月热销活动**

                1. 🎁 **到店礼**：到店试驾即送精美保温杯一个
                2. 💰 **现金优惠**：部分车型直降 **5000-20000元**
                3. 🎫 **订车礼**：订车即送价值 3888元 装潢大礼包
                4. 🎲 **抽奖活动**：购车参与砸金蛋，100%中奖
                5. 👨‍👩‍👧‍👦 **老带新**：老客户推荐新客户购车，双方各得 500元 油卡

                💡 **热门车型优惠一览**：
                - 比亚迪秦PLUS DM-i 优惠后 **7.98万起**
                - 比亚迪汉 DM-i 优惠后 **16.98万起**
                - 哈弗H6 综合优惠 **最高1.5万元**

                具体车型价格随时问我哦～""";
    }
}
