package com.hc24.springaidemo.port;

import com.hc24.springaidemo.agent.AdvisorReActAgent;

import jakarta.annotation.PreDestroy;
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

    private final AdvisorReActAgent advisorReActAgent;
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ReActStreamController(AdvisorReActAgent advisorReActAgent) {
        this.advisorReActAgent = advisorReActAgent;
    }

    @PreDestroy
    void shutdownStreamExecutor() {
        this.streamExecutor.close();
    }

    @GetMapping(path = "/react/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reactStream(String question, String conversationId) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        this.streamExecutor.execute(() -> emitReact(emitter, conversationId, question));
        return emitter;
    }

    private void emitReact(SseEmitter emitter, String conversationId, String userQuestion) {
        try {
            this.advisorReActAgent.stream(conversationId, userQuestion, token -> send(emitter, "delta", token));
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
