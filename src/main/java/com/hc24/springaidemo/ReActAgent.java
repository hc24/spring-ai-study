package com.hc24.springaidemo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReActAgent {

    public static final ReActListener NOOP = new ReActListener() {
    };

    private final ChatClient chatClient;
    private final ToolCallingManager toolCallingManager;
    private final ToolCallback[] toolCallbacks;
    private final int maxRound;

    public ReActAgent(ChatModel chatModel, WeatherTool weatherTool, ToolCallingManager toolCallingManager,
            @Value("${react.max-round}") int maxRound) {
        // Keep tool execution in this loop. ChatClient would otherwise run tools itself.
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(advisor -> advisor.param(
                        ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), false))
                .build();
        this.toolCallingManager = toolCallingManager;
        this.toolCallbacks = ToolCallbacks.from(weatherTool);
        this.maxRound = maxRound;
    }

    public String react(String userQuestion, ReActListener listener) {
        // 1. 构建用户Message
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(userQuestion));

        ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                .toolCallbacks(this.toolCallbacks)
                .build();

        // 2. 循环ReAct
        for (int i = 0; i < this.maxRound; i++) {
            //  ① 先把用户message和工具扔给模型
            ChatResponse response = chatClient.prompt()
                    .messages(messages)
                    .tools((Object[]) this.toolCallbacks)
                    .call()
                    .chatResponse();

            // ② 解析模型返回的Message
            AssistantMessage assistantMsg = response != null && response.getResult() != null
                    ? response.getResult().getOutput() : null;
            if (assistantMsg == null) {
                return "模型未返回结果";
            }

            // ③ 模型告诉Agent没有工具调用
            if (!assistantMsg.hasToolCalls()) {
                String text = assistantMsg.getText();
                return text != null ? text : "";
            }

            // ③ 模型告诉Agent需要工具调用
            // History passed in must not already contain this assistant message.
            // executeToolCalls appends the assistant message and the tool response.
            for (AssistantMessage.ToolCall toolCall : assistantMsg.getToolCalls()) {
                listener.onToolCall(toolCall);
            }
            ToolExecutionResult toolResult = toolCallingManager.executeToolCalls(
                    new Prompt(messages, toolOptions), response);
            messages = new ArrayList<>(toolResult.conversationHistory());
            publishToolResults(messages, listener);

            // ④ 工具直接返回结果了
            if (toolResult.returnDirect()) {
                return ToolExecutionResult.buildGenerations(toolResult).stream()
                        .map(generation -> generation.getOutput().getText())
                        .filter(text -> text != null)
                        .collect(Collectors.joining("\n"));
            }
        }
        return "达到最大思考轮次，任务终止";
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
