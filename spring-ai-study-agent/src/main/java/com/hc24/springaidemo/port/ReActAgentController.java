package com.hc24.springaidemo.port;

import com.hc24.springaidemo.agent.AdvisorReActAgent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReActAgentController {

    private final AdvisorReActAgent advisorReActAgent;

    public ReActAgentController(AdvisorReActAgent advisorReActAgent) {
        this.advisorReActAgent = advisorReActAgent;
    }

    @GetMapping("/react")
    public String reactAgent(String question, String conversationId) {
        return this.advisorReActAgent.chat(conversationId, question);
    }

}
