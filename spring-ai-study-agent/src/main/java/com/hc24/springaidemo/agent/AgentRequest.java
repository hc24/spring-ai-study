package com.hc24.springaidemo.agent;

/**
 * One turn sent to the agent. {@code root} is the directory the user granted,
 * and is empty when no directory is selected.
 */
public record AgentRequest(String question, String conversationId, String root) {
}
