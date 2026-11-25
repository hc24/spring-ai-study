package com.hc24.springaidemo.usage;

/**
 * Thrown when process-local spend has already reached the practice budget.
 * The call that crosses the budget is still served; the next call is refused.
 */
public class BudgetExceededException extends RuntimeException {

    public BudgetExceededException() {
        super("练习预算已用尽");
    }

}
