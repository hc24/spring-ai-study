package com.hc24.springaidemo.usage;

import java.math.BigDecimal;

/**
 * One conversation's cumulative charge plus the process-wide remaining budget.
 * Token and cost fields are zero when the conversation has no recorded calls.
 */
public record UsageSnapshot(
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal estimatedUsd,
        BigDecimal remainingUsd) {
}
