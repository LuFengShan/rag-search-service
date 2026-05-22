package com.example.rag.skill;

import com.example.rag.dto.response.AnswerResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ActivitySkill implements AgentSkill {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile(".*(有什么优惠|优惠活动|现在优惠|最近活动|促销活动|打折|搞活动|降价|便宜多少).*"),
            Pattern.compile(".*(贷款政策|金融方案|分期方案|首付多少|月供多少|0首付|零首付).*"),
            Pattern.compile(".*(置换补贴|以旧换新|旧车置换|报废补贴|国家补贴|地方补贴).*"),
            Pattern.compile(".*(购置税|购置税减免|免购置税|上牌费|保险多少钱).*")
    );

    @Override
    public boolean canHandle(String userInput) {
        String input = userInput.trim().toLowerCase();
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(input).matches()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public AnswerResponse execute(String userInput) {
        String input = userInput.trim().toLowerCase();

        if (input.contains("贷款") || input.contains("首付") || input.contains("月供") || input.contains("分期")) {
            return AnswerResponse.builder()
                    .answer("""
                            🏦 **本月金融方案**

                            1. **0首付方案** — 指定车型享0首付，月供仅需1980元起
                            2. **3年0利率** — 首付30%以上，享受3年0利率金融补贴
                            3. **5年超长贷** — 月供低至1580元，轻松还款无压力

                            💡 举例：秦PLUS DM-i 优惠后7.98万
                            → 首付0元，分60期，月供约1680元
                            → 首付2.4万（30%），分36期0利率，月供约1550元

                            需要我帮您算具体车型的月供吗？""")
                    .sources(List.of())
                    .confidence(1.0f)
                    .build();
        }

        if (input.contains("置换") || input.contains("以旧换新") || input.contains("补贴")) {
            return AnswerResponse.builder()
                    .answer("""
                            ♻️ **置换补贴政策（截至%s）**

                            1. 🚗 **厂家置换补贴**：旧车置换新车，最高补贴 **8000元**
                            2. 🏛️ **国家报废补贴**：报废国三及以下排放标准车辆，补贴 **2万元**
                            3. 🏙️ **地方消费券**：限时发放，最高可领 **5000元** 购车券

                            📋 置换所需材料：
                            - 旧车行驶证、登记证书
                            - 车主身份证
                            - 旧车交强险保单

                            💡 我们提供一站式置换服务，旧车评估→过户→提新车，最快当天搞定！""".formatted(
                                    LocalDate.now().plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))))
                    .sources(List.of())
                    .confidence(1.0f)
                    .build();
        }

        return AnswerResponse.builder()
                .answer("""
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

                        具体车型价格随时问我哦～""")
                .sources(List.of())
                .confidence(1.0f)
                .build();
    }
}
