package com.example.rag.skill;

import com.example.rag.dto.response.AnswerResponse;

public interface AgentSkill {

    boolean canHandle(String userInput);

    AnswerResponse execute(String userInput);
}
