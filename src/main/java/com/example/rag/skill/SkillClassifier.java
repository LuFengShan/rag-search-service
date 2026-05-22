package com.example.rag.skill;

import com.example.rag.dto.response.AnswerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillClassifier {

    private final List<AgentSkill> skills;

    public AnswerResponse matchAndExecute(String userInput) {
        for (AgentSkill skill : skills) {
            if (skill.canHandle(userInput)) {
                log.info("Skill matched: {} for input: {}", skill.getClass().getSimpleName(),
                        userInput.length() > 40 ? userInput.substring(0, 40) + "..." : userInput);
                return skill.execute(userInput);
            }
        }
        return null;
    }
}
