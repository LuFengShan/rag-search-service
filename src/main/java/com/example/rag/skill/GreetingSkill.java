package com.example.rag.skill;

import com.example.rag.dto.response.AnswerResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class GreetingSkill implements AgentSkill {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("^(你好|您好|hi|hello|嗨|哈喽|在吗|在不在|有人在吗|有人吗)[\\s\\S]*$"),
            Pattern.compile("^(早上好|中午好|下午好|晚上好|good\\s*morning|good\\s*evening)[\\s\\S]*$"),
            Pattern.compile("^你是谁|你叫什么|你是干什么的|你能做什么|介绍一下你自己[\\s\\S]*$")
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
        String answer = """
                👋 您好！我是汽车销售顾问小助手～

                我可以帮您：
                🚗 **车型咨询**：介绍各品牌车型的配置、参数和亮点
                💰 **价格查询**：查询最新指导价和落地价参考
                ⚖️ **车型对比**：对比不同车型的优劣势，帮您决策
                🎯 **预算推荐**：根据预算范围，推荐最合适的车型
                📞 **联系方式**：需要线下看车随时问我

                有什么想了解的车型，尽管问我吧！""";

        return AnswerResponse.builder()
                .answer(answer)
                .sources(List.of())
                .confidence(1.0f)
                .build();
    }
}
