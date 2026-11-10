package com.hc24.springaidemo;

import jakarta.annotation.PreDestroy;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
public class ReActAgentController {

    private static final ReActListener NOOP = new ReActListener() {
    };

    private final ChatClient chatClient;
    private final ToolCallingManager toolCallingManager;
    private final ToolCallback[] toolCallbacks;
    private final int maxRound;
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ReActAgentController(ChatModel chatModel, WeatherTool weatherTool, ToolCallingManager toolCallingManager,
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

    @PreDestroy
    void shutdownStreamExecutor() {
        this.streamExecutor.close();
    }

    @GetMapping("/react")
    public String reactAgent(String userQuestion) {
        return react(userQuestion, NOOP);
    }

    @GetMapping(path = "/react/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reactStream(String userQuestion) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        this.streamExecutor.execute(() -> emitReact(emitter, userQuestion));
        return emitter;
    }

    private void emitReact(SseEmitter emitter, String userQuestion) {
        try {
            String answer = react(userQuestion, new ReActListener() {
                @Override
                public void onToolCall(AssistantMessage.ToolCall toolCall) {
                    send(emitter, "tool_call", toolCall.name() + " " + toolCall.arguments());
                }

                @Override
                public void onToolResult(ToolResponseMessage.ToolResponse toolResponse) {
                    send(emitter, "tool_result", toolResponse.responseData());
                }
            });
            send(emitter, "answer", answer);
            emitter.complete();
        }
        catch (Exception ex) {
            try {
                send(emitter, "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                emitter.complete();
            }
            catch (Exception ignored) {
                emitter.completeWithError(ex);
            }
        }
    }

    private String react(String userQuestion, ReActListener listener) {
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

    private static void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data == null ? "" : data));
        }
        catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private interface ReActListener {

        default void onToolCall(AssistantMessage.ToolCall toolCall) {
        }

        default void onToolResult(ToolResponseMessage.ToolResponse toolResponse) {
        }

    }

}
