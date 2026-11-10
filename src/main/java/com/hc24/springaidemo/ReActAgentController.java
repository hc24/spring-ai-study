package com.hc24.springaidemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReActAgentController {

    private final ReActAgent reActAgent;

    public ReActAgentController(ReActAgent reActAgent) {
        this.reActAgent = reActAgent;
    }

    @GetMapping("/react")
    public String reactAgent(String userQuestion) {
        return this.reActAgent.react(userQuestion, ReActAgent.NOOP);
    }

}
