package com.hc24.springaidemo;

import jakarta.annotation.PreDestroy;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class ReActStreamController {

    private final ReActAgent reActAgent;
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ReActStreamController(ReActAgent reActAgent) {
        this.reActAgent = reActAgent;
    }

    @PreDestroy
    void shutdownStreamExecutor() {
        this.streamExecutor.close();
    }

    @GetMapping(path = "/react/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reactStream(String question) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        this.streamExecutor.execute(() -> emitReact(emitter, question));
        return emitter;
    }

    private void emitReact(SseEmitter emitter, String userQuestion) {
        try {
            String answer = this.reActAgent.react(userQuestion, new ReActAgent.ReActListener() {
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

    private static void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data == null ? "" : data));
        }
        catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
