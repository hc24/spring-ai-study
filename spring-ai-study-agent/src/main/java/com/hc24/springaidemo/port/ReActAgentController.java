package com.hc24.springaidemo.port;

import com.hc24.springaidemo.agent.AdvisorReActAgent;
import com.hc24.springaidemo.agent.AgentRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReActAgentController {

    private final AdvisorReActAgent advisorReActAgent;

    public ReActAgentController(AdvisorReActAgent advisorReActAgent) {
        this.advisorReActAgent = advisorReActAgent;
    }

    @GetMapping("/react")
    public String reactAgent(AgentRequest request) {
        return this.advisorReActAgent.chat(request);
    }

}
