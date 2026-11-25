package com.hc24.springaidemo.usage;

import com.hc24.springaidemo.agent.ConversationId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsageBudgetTest {

    @Test
    void accumulatesByConversationAndRefusesTheNextCallAtBudget() {
        UsageBudget budget = new UsageBudget(new BigDecimal("0.20"), new BigDecimal("0.80"), new BigDecimal("1.00"));

        ConversationId tripA = ConversationId.of("trip-a");
        budget.ensureWithinBudget();
        budget.record(tripA, 1_000, 500, 1_500);
        budget.record(tripA, 5_000_000, 0, 5_000_000);

        UsageSnapshot trip = budget.snapshot(tripA);
        assertEquals(5_001_000, trip.promptTokens());
        assertEquals(500, trip.completionTokens());
        assertEquals(5_001_500, trip.totalTokens());
        assertEquals(0, trip.estimatedUsd().compareTo(new BigDecimal("1.00060000")));
        assertEquals(0, trip.remainingUsd().compareTo(BigDecimal.ZERO));

        UsageSnapshot empty = budget.snapshot(ConversationId.of("missing"));
        assertEquals(0, empty.promptTokens());
        assertEquals(0, empty.completionTokens());
        assertEquals(0, empty.totalTokens());
        assertEquals(0, empty.estimatedUsd().compareTo(BigDecimal.ZERO));
        assertEquals(0, empty.remainingUsd().compareTo(BigDecimal.ZERO));

        assertThrows(BudgetExceededException.class, budget::ensureWithinBudget);
    }

    @Test
    void generatedConversationIdIsStoredButNotReturnedForABlankQuery() {
        UsageBudget budget = new UsageBudget(new BigDecimal("0.20"), new BigDecimal("0.80"), new BigDecimal("1.00"));
        ConversationId generated = ConversationId.of("  ");
        assertFalse(generated.suppliedByCaller());
        budget.record(generated, 100, 100, 200);

        UsageSnapshot hidden = budget.snapshot(generated);
        assertEquals(200, hidden.totalTokens());

        UsageSnapshot queried = budget.snapshot(ConversationId.of(null));
        assertEquals(0, queried.totalTokens());
        assertEquals(0, queried.estimatedUsd().compareTo(BigDecimal.ZERO));
        assertEquals(0, queried.remainingUsd().compareTo(new BigDecimal("0.99990000")));
        budget.ensureWithinBudget();
    }

}
