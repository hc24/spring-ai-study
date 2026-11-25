package com.hc24.springaidemo.agent;

import java.util.UUID;

/**
 * Conversation key used by memory and the usage budget.
 * A blank raw value still gets an id so the current call can finish, but
 * {@link #suppliedByCaller()} is false and that charge cannot be queried later.
 */
public record ConversationId(String value, boolean suppliedByCaller) {

    public ConversationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("conversation id is required");
        }
    }

    public static ConversationId of(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ConversationId(UUID.randomUUID().toString(), false);
        }
        return new ConversationId(raw, true);
    }

}
