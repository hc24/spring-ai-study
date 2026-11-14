package com.hc24.springaidemo.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hand-written tool loop, kept as a readable ReAct exercise. HTTP endpoints use
 * {@link AdvisorReActAgent}. Each model call here is one round, so callers can
 * observe tool calls before the next round starts.
 */
@Component
public class ManualReActAgent {

    public static final ReActListener NOOP = new ReActListener() {
    };

    private final ChatClient chatClient;
    private final ToolCallingManager toolCallingManager;
    private final ChatMemory chatMemory;
    private final ToolCallback[] toolCallbacks;

    @Value("${react.max-round}")
    private int maxRound;

    @Value("${react.system-message}")
    private String systemMessage;

    public ManualReActAgent(ChatModel chatModel, ToolCatalog toolCatalog, ToolCallingManager toolCallingManager,
                            ChatMemory chatMemory) {
        // Keep tool execution in this loop. ChatClient would otherwise run tools itself.
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(advisor -> advisor.param(
                        ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false))
                .build();
        this.toolCallingManager = toolCallingManager;
        this.chatMemory = chatMemory;
        this.toolCallbacks = toolCatalog.getToolCallbacks();
    }

    public String react(String userQuestion, ReActListener listener) {
        return react(null, userQuestion, listener);
    }

    public String react(String conversationId, String userQuestion, ReActListener listener) {
        List<Message> history = historyOf(conversationId);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(this.systemMessage));
        messages.addAll(history);
        messages.add(new UserMessage(userQuestion));
        int turnStart = messages.size() - 1;

        String answer = runTurn(messages, listener);
        if (answer != null) {
            remember(conversationId, messages.subList(turnStart, messages.size()));
        }
        return answer != null ? answer : "模型未返回结果";
    }

    private String runTurn(List<Message> messages, ReActListener listener) {
        ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(this.toolCallbacks)
                .build();

        for (int i = 0; i < this.maxRound; i++) {
            ChatResponse response = chatClient.prompt()
                    .messages(messages)
                    .tools((Object[]) this.toolCallbacks)
                    .call()
                    .chatResponse();

            AssistantMessage assistantMsg = response != null && response.getResult() != null
                    ? response.getResult().getOutput() : null;
            if (assistantMsg == null) {
                return null;
            }

            if (!assistantMsg.hasToolCalls()) {
                messages.add(assistantMsg);
                String text = assistantMsg.getText();
                return text != null ? text : "";
            }

            // History passed in must not already contain this assistant message.
            // executeToolCalls appends the assistant message and the tool response.
            for (AssistantMessage.ToolCall toolCall : assistantMsg.getToolCalls()) {
                listener.onToolCall(toolCall);
            }
            ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(
                    new Prompt(messages, toolOptions), response);
            messages.clear();
            messages.addAll(toolResult.conversationHistory());
            publishToolResults(messages, listener);

            if (toolResult.returnDirect()) {
                return ToolExecutionResult.buildGenerations(toolResult).stream()
                        .map(generation -> generation.getOutput().getText())
                        .filter(text -> text != null)
                        .collect(Collectors.joining("\n"));
            }
        }
        return "达到最大思考轮次，任务终止";
    }

    private List<Message> historyOf(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return this.chatMemory.get(conversationId).stream()
                .filter(message -> !(message instanceof SystemMessage))
                .toList();
    }

    private void remember(String conversationId, List<Message> turn) {
        if (conversationId == null || conversationId.isBlank() || turn.isEmpty()) {
            return;
        }
        this.chatMemory.add(conversationId, new ArrayList<>(turn));
    }

    private static void publishToolResults(List<Message> messages, ReActListener listener) {
        if (messages.isEmpty() || !(messages.get(messages.size() - 1) instanceof ToolResponseMessage toolResponse)) {
            return;
        }
        for (ToolResponseMessage.ToolResponse response : toolResponse.getResponses()) {
            listener.onToolResult(response);
        }
    }

    public interface ReActListener {

        default void onToolCall(AssistantMessage.ToolCall toolCall) {
        }

        default void onToolResult(ToolResponseMessage.ToolResponse toolResponse) {
        }

    }

}
