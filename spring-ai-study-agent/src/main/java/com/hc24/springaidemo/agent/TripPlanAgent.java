package com.hc24.springaidemo.agent;

import com.hc24.springaidemo.tool.LocalFilesTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.StructuredOutputValidationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One non-streaming call that returns a {@link TripPlan}. Chat memory is not attached,
 * so the JSON answer is not written into the normal conversation.
 */
@Component
public class TripPlanAgent {

    private static final String SYSTEM_MESSAGE = """
            你是旅行助手。先按需要调用天气、酒店、小吃和景点等工具。
            最后只按给定的 JSON 结构作答，不要写结构以外的说明。
            """;

    private final ChatClient chatClient;
    private final BeanOutputConverter<TripPlan> converter = new BeanOutputConverter<>(TripPlan.class);
    private final String fileToolHint;

    public TripPlanAgent(ChatModel chatModel, ToolCatalog toolCatalog,
                         ObjectProvider<RetrievalAugmentationAdvisor> retrievalAdvisor,
                         @Value("${react.file-tool-hint}") String fileToolHint) {
        this.fileToolHint = fileToolHint;
        List<Advisor> advisors = new ArrayList<>();
        retrievalAdvisor.ifAvailable(advisors::add);
        advisors.add(StructuredOutputValidationAdvisor.builder()
                .outputType(TripPlan.class)
                .maxRepeatAttempts(2)
                .build());
        this.chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCatalog.getToolCallbacks())
                .defaultAdvisors(advisors)
                .defaultSystem(SYSTEM_MESSAGE)
                .build();
    }

    public TripPlan plan(AgentRequest request, boolean nativeStructuredOutput) {
        var call = prompt(request).call();
        TripPlan plan = nativeStructuredOutput
                ? call.entity(this.converter, spec -> spec.useProviderStructuredOutput())
                : call.entity(this.converter);
        if (plan == null) {
            throw new IllegalStateException("Model returned an empty trip plan");
        }
        return plan;
    }

    private ChatClient.ChatClientRequestSpec prompt(AgentRequest request) {
        boolean granted = request.root() != null && !request.root().isBlank();
        String question = request.question() == null ? "" : request.question();
        String userText = granted ? this.fileToolHint + "\n" + question : question;
        return this.chatClient.prompt()
                .user(userText)
                // Tool methods declare ToolContext, so the map must exist even when no directory is granted.
                .toolContext(Map.of(LocalFilesTool.ROOT_KEY, granted ? request.root() : ""));
    }

}
