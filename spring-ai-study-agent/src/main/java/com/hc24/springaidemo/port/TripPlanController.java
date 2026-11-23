package com.hc24.springaidemo.port;

import com.hc24.springaidemo.agent.AgentRequest;
import com.hc24.springaidemo.agent.TripPlan;
import com.hc24.springaidemo.agent.TripPlanAgent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TripPlanController {

    private final TripPlanAgent tripPlanAgent;

    public TripPlanController(TripPlanAgent tripPlanAgent) {
        this.tripPlanAgent = tripPlanAgent;
    }

    @GetMapping("/react/plan")
    public TripPlan plan(AgentRequest request, @RequestParam(name = "native", defaultValue = "false") boolean nativeStructuredOutput) {
        return this.tripPlanAgent.plan(request, nativeStructuredOutput);
    }

}
