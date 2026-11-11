package com.hc24.springaidemo.port;

import com.hc24.springaidemo.agent.ReActAgent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReActAgentController {

    private final ReActAgent reActAgent;

    public ReActAgentController(ReActAgent reActAgent) {
        this.reActAgent = reActAgent;
    }

    @GetMapping("/react")
    public String reactAgent(String question) {
        return this.reActAgent.react(question, ReActAgent.NOOP);
    }

}
