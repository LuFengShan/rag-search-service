package com.example.rag.skill;

import com.example.rag.dto.response.AnswerResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ContactSkill implements AgentSkill {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile(".*(怎么联系|联系电话|电话多少|电话号码|联系方式|加(个)?微信|微信号|扫码|扫一下).*"),
            Pattern.compile(".*(地址在哪|门店在哪|4s店在哪|实体店|线下店|在哪里看车|去哪看车|试驾怎么约|预约试驾).*"),
            Pattern.compile(".*(售后电话|保养预约|维修电话|客服电话|投诉电话).*")
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

        if (input.contains("售后") || input.contains("保养") || input.contains("维修")) {
            return AnswerResponse.builder()
                    .answer("""
                            🔧 **售后服务联系方式**

                            📞 售后热线：400-888-9999（24小时）
                            🏪 预约保养：打开微信小程序"XX汽车售后"即可预约
                            📍 最近服务中心：**XX区XX路XX号**（距您约3公里）

                            需要帮您直接预约吗？""")
                    .sources(List.of())
                    .confidence(1.0f)
                    .build();
        }

        return AnswerResponse.builder()
                .answer("""
                        📞 **联系我们**

                        🏪 **线下体验中心**：XX区汽车大道168号（地铁2号线"汽车城"站B出口）
                        📱 **咨询热线**：400-123-8888
                        💬 **微信客服**：搜索"XX汽车官方旗舰店"添加企业微信
                        🕐 **营业时间**：周一至周日 9:00-21:00

                        🚗 **试驾预约**：回复"预约"或直接告诉我您想试驾的车型，我帮您安排！

                        现在到店试驾即送精美礼品一份 🎁""")
                .sources(List.of())
                .confidence(1.0f)
                .build();
    }
}
