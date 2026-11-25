package com.hc24.springaidemo.usage;

import java.math.BigDecimal;

/**
 * Cumulative token use and the estimated USD cost for one conversation.
 */
public record TokenCharge(long promptTokens, long completionTokens, long totalTokens, BigDecimal estimatedUsd) {

    public TokenCharge {
        if (estimatedUsd == null) {
            estimatedUsd = BigDecimal.ZERO;
        }
    }

    public static TokenCharge zero() {
        return new TokenCharge(0, 0, 0, BigDecimal.ZERO);
    }

    public TokenCharge plus(TokenCharge other) {
        return new TokenCharge(
                this.promptTokens + other.promptTokens,
                this.completionTokens + other.completionTokens,
                this.totalTokens + other.totalTokens,
                this.estimatedUsd.add(other.estimatedUsd));
    }

}
