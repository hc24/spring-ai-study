package com.hc24.springaidemo.usage;

import com.hc24.springaidemo.agent.ConversationId;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Records one charge per user turn, outside the tool loop, so each tool round
 * is not billed again. The framework has already folded those rounds into the
 * final {@link Usage}.
 */
@Component
public class UsageRecordingAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * Outside retrieval ({@code DEFAULT_ORDER - 1}) and the tool loop.
     */
    public static final int ORDER = ToolCallingAdvisor.DEFAULT_ORDER - 2;

    private final UsageBudget usageBudget;

    public UsageRecordingAdvisor(UsageBudget usageBudget) {
        this.usageBudget = usageBudget;
    }

    @Override
    public String getName() {
        return "UsageRecordingAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        this.usageBudget.ensureWithinBudget();
        ConversationId conversationId = conversationId(request);
        ChatClientResponse response = chain.nextCall(request);
        this.usageBudget.record(conversationId, usageOf(response));
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        this.usageBudget.ensureWithinBudget();
        ConversationId conversationId = conversationId(request);
        ObservedUsage observed = new ObservedUsage();
        return chain.nextStream(request)
                .doOnNext(response -> observed.accept(response.chatResponse()))
                .doOnComplete(() -> this.usageBudget.record(
                        conversationId, observed.promptTokens, observed.completionTokens, observed.totalTokens));
    }

    private static ConversationId conversationId(ChatClientRequest request) {
        Object value = request.context().get(ChatMemory.CONVERSATION_ID);
        return ConversationId.of(value == null ? null : value.toString());
    }

    private static Usage usageOf(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null || response.chatResponse().getMetadata() == null) {
            return null;
        }
        return response.chatResponse().getMetadata().getUsage();
    }

    /**
     * Keeps the last positive counts. Usage-bearing stream chunks already include
     * earlier tool rounds.
     */
    private static final class ObservedUsage {
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;

        private void accept(ChatResponse response) {
            if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return;
            }
            Usage usage = response.getMetadata().getUsage();
            if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
                this.promptTokens = usage.getPromptTokens();
            }
            if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0) {
                this.completionTokens = usage.getCompletionTokens();
            }
            if (usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
                this.totalTokens = usage.getTotalTokens();
            }
        }
    }

}
