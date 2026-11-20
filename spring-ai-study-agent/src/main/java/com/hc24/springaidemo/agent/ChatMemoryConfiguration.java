package com.hc24.springaidemo.agent;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure.JdbcChatMemoryRepositoryAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
public class ChatMemoryConfiguration {

    @Bean
    @ConditionalOnProperty(name = "react.memory-store", havingValue = "memory", matchIfMissing = true)
    ChatMemory chatMemory(@Value("${react.memory-max-messages:40}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * Turns on Spring's DataSource and JDBC chat-memory auto-configuration.
     * Connection settings stay in spring.datasource.*.
     */
    @Configuration
    @ConditionalOnProperty(name = "react.memory-store", havingValue = "jdbc")
    @Import({DataSourceAutoConfiguration.class, JdbcChatMemoryRepositoryAutoConfiguration.class})
    static class JdbcChatMemory {

        @Bean
        ChatMemory chatMemory(JdbcChatMemoryRepository repository,
                              @Value("${react.memory-max-messages:40}") int maxMessages) {
            return MessageWindowChatMemory.builder()
                    .chatMemoryRepository(repository)
                    .maxMessages(maxMessages)
                    .build();
        }

    }

}
