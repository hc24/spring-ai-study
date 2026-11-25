package com.hc24.springaidemo.usage;

import com.hc24.springaidemo.agent.ConversationId;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Process-local meter. Charges are keyed by conversation id and the total spend
 * resets when the process restarts. Prices are practice placeholders.
 */
@Component
public class UsageBudget {

    private static final BigDecimal MILLION = new BigDecimal("1000000");
    private static final int COST_SCALE = 8;

    private final BigDecimal promptUsdPerMillion;
    private final BigDecimal completionUsdPerMillion;
    private final BigDecimal budgetUsd;
    private final Map<String, TokenCharge> charges = new HashMap<>();
    private final Object monitor = new Object();
    private BigDecimal spentUsd = BigDecimal.ZERO;

    public UsageBudget(
            @Value("${react.usage.prompt-usd-per-million}") BigDecimal promptUsdPerMillion,
            @Value("${react.usage.completion-usd-per-million}") BigDecimal completionUsdPerMillion,
            @Value("${react.usage.budget-usd}") BigDecimal budgetUsd) {
        this.promptUsdPerMillion = promptUsdPerMillion;
        this.completionUsdPerMillion = completionUsdPerMillion;
        this.budgetUsd = budgetUsd;
    }

    public void ensureWithinBudget() {
        synchronized (this.monitor) {
            if (this.spentUsd.compareTo(this.budgetUsd) >= 0) {
                throw new BudgetExceededException();
            }
        }
    }

    public void record(ConversationId conversationId, Usage usage) {
        if (usage == null) {
            return;
        }
        long promptTokens = count(usage.getPromptTokens());
        long completionTokens = count(usage.getCompletionTokens());
        long totalTokens = count(usage.getTotalTokens());
        record(conversationId, promptTokens, completionTokens, totalTokens);
    }

    public void record(ConversationId conversationId, long promptTokens, long completionTokens, long totalTokens) {
        if (conversationId == null) {
            return;
        }
        if (promptTokens == 0 && completionTokens == 0 && totalTokens == 0) {
            return;
        }
        TokenCharge charge = price(promptTokens, completionTokens, totalTokens);
        synchronized (this.monitor) {
            this.charges.merge(conversationId.value(), charge, TokenCharge::plus);
            this.spentUsd = this.spentUsd.add(charge.estimatedUsd());
        }
    }

    public UsageSnapshot snapshot(ConversationId conversationId) {
        synchronized (this.monitor) {
            TokenCharge charge = conversationId == null
                    ? TokenCharge.zero()
                    : this.charges.getOrDefault(conversationId.value(), TokenCharge.zero());
            return new UsageSnapshot(
                    charge.promptTokens(),
                    charge.completionTokens(),
                    charge.totalTokens(),
                    charge.estimatedUsd(),
                    remainingUsd());
        }
    }

    private BigDecimal remainingUsd() {
        BigDecimal remaining = this.budgetUsd.subtract(this.spentUsd);
        if (remaining.signum() < 0) {
            return BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP);
        }
        return remaining;
    }

    private TokenCharge price(long promptTokens, long completionTokens, long totalTokens) {
        BigDecimal estimated = cost(promptTokens, this.promptUsdPerMillion)
                .add(cost(completionTokens, this.completionUsdPerMillion));
        long total = totalTokens > 0 ? totalTokens : promptTokens + completionTokens;
        return new TokenCharge(promptTokens, completionTokens, total, estimated);
    }

    private static BigDecimal cost(long tokens, BigDecimal usdPerMillion) {
        return BigDecimal.valueOf(tokens)
                .multiply(usdPerMillion)
                .divide(MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    private static long count(Integer tokens) {
        return tokens == null ? 0 : tokens.longValue();
    }

}
