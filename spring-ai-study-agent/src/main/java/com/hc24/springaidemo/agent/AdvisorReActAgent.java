package com.hc24.springaidemo.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Tool loop owned by Spring AI's {@code ToolCallingAdvisor}. One prompt call
 * runs every tool round inside the client. Memory is stored by
 * {@link MessageChatMemoryAdvisor}, which keeps the user message and the final
 * assistant answer, not each tool round.
 */
@Component
public class AdvisorReActAgent {

    private final ChatClient chatClient;
    private final String systemMessage;

    public AdvisorReActAgent(ChatModel chatModel, ToolCatalog toolCatalog, ChatMemory chatMemory,
                             @Value("${react.system-message}") String systemMessage) {
        this.systemMessage = systemMessage;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultTools((Object[]) toolCatalog.getToolCallbacks())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // Inside the tool loop so each model round and tool call is logged.
                        SimpleLoggerAdvisor.builder().order(ToolCallingAdvisor.DEFAULT_ORDER + 1).build())
                .build();
    }

    public String chat(String conversationId, String question) {
        String content = prompt(conversationId, question).call().content();
        return content != null ? content : "";
    }

    public void stream(String conversationId, String question, Consumer<String> onDelta) {
        prompt(conversationId, question).stream()
                .content()
                .filter(token -> token != null && !token.isEmpty())
                .doOnNext(onDelta)
                .blockLast();
    }

    private ChatClient.ChatClientRequestSpec prompt(String conversationId, String question) {
        String id = conversationId == null || conversationId.isBlank() ? UUID.randomUUID().toString() : conversationId;
        return this.chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, id))
                .system(this.systemMessage)
                .user(question);
    }

}
