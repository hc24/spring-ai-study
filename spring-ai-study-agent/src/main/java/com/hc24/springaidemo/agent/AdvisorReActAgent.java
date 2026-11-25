package com.hc24.springaidemo.agent;

import com.hc24.springaidemo.tool.LocalFilesTool;
import com.hc24.springaidemo.usage.UsageRecordingAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tool loop owned by Spring AI's {@code ToolCallingAdvisor}. One prompt call
 * runs every tool round inside the client. Memory is stored by
 * {@link MessageChatMemoryAdvisor}, which keeps the user message and the final
 * assistant answer, not each tool round. Token charges are recorded by
 * {@link UsageRecordingAdvisor}.
 */
@Component
public class AdvisorReActAgent {

    private final ChatClient chatClient;
    private final String systemMessage;

    @Value("${react.file-tool-hint}")
    private String fileToolHint;

    public AdvisorReActAgent(ChatModel chatModel, ToolCatalog toolCatalog, ChatMemory chatMemory,
                             ObjectProvider<RetrievalAugmentationAdvisor> retrievalAdvisor,
                             UsageRecordingAdvisor usageRecordingAdvisor,
                             @Value("${react.system-message}") String systemMessage) {
        this.systemMessage = systemMessage;
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(usageRecordingAdvisor);
        advisors.add(MessageChatMemoryAdvisor.builder(chatMemory).build());
        retrievalAdvisor.ifAvailable(advisors::add);
        advisors.add(SimpleLoggerAdvisor.builder().order(ToolCallingAdvisor.DEFAULT_ORDER + 1).build());
        this.chatClient = ChatClient.builder(chatModel)
                .defaultTools(toolCatalog.getToolCallbacks())
                .defaultAdvisors(advisors)
                .build();
    }

    public String chat(AgentRequest request) {
        String content = prompt(request).call().content();
        return content != null ? content : "";
    }

    public void stream(AgentRequest request, Consumer<String> onDelta) {
        prompt(request).stream()
                .content()
                .filter(token -> token != null && !token.isEmpty())
                .doOnNext(onDelta)
                .blockLast();
    }

    private ChatClient.ChatClientRequestSpec prompt(AgentRequest request) {
        ConversationId conversationId = ConversationId.of(request.conversationId());
        boolean granted = request.root() != null && !request.root().isBlank();
        String userText = granted ? this.fileToolHint + "\n" + request.question() : request.question();
        return this.chatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId.value()))
                .system(this.systemMessage)
                .user(userText)
                // Tool methods declare ToolContext, so the map must exist even when no directory is granted.
                .toolContext(Map.of(LocalFilesTool.ROOT_KEY, granted ? request.root() : ""));
    }

}
