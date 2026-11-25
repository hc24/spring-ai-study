package com.hc24.springaidemo.port;

import com.hc24.springaidemo.agent.AdvisorReActAgent;
import com.hc24.springaidemo.agent.AgentRequest;
import com.hc24.springaidemo.agent.ConversationId;
import com.hc24.springaidemo.usage.BudgetExceededException;
import com.hc24.springaidemo.usage.UsageBudget;
import com.hc24.springaidemo.usage.UsageSnapshot;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReActAgentController {

    private final AdvisorReActAgent advisorReActAgent;
    private final UsageBudget usageBudget;

    public ReActAgentController(AdvisorReActAgent advisorReActAgent, UsageBudget usageBudget) {
        this.advisorReActAgent = advisorReActAgent;
        this.usageBudget = usageBudget;
    }

    @GetMapping("/react")
    public ResponseEntity<String> reactAgent(AgentRequest request) {
        try {
            return ResponseEntity.ok(this.advisorReActAgent.chat(request));
        }
        catch (BudgetExceededException ex) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ex.getMessage());
        }
    }

    @GetMapping("/react/usage")
    public UsageSnapshot usage(@RequestParam(required = false) String conversationId) {
        return this.usageBudget.snapshot(ConversationId.of(conversationId));
    }

}
