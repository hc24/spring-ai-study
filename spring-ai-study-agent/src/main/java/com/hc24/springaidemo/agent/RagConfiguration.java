package com.hc24.springaidemo.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.io.File;

@Configuration
@ConditionalOnProperty(name = "app.rag-enabled", havingValue = "true", matchIfMissing = true)
public class RagConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RagConfiguration.class);

    @Bean
    ApplicationRunner missingVectorStoreWarning(
            @Value("${rag.store-file:../data/vector-store.json}") String storeFile) {
        return args -> {
            if (!new File(storeFile).isFile()) {
                log.warn("RAG store file not found at {}. Run spring-ai-study-indexer first.", storeFile);
            }
        };
    }

    @Bean
    @Conditional(VectorStoreFilePresent.class)
    VectorStore ragVectorStore(EmbeddingModel embeddingModel,
                               @Value("${rag.store-file:../data/vector-store.json}") String storeFile) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        store.load(new File(storeFile));
        return store;
    }

    @Bean
    @Conditional(VectorStoreFilePresent.class)
    RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(VectorStore ragVectorStore,
                                                              @Value("${react.rag-prompt}") String ragPrompt) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(ragVectorStore)
                        .similarityThreshold(0.4)
                        .topK(3)
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .promptTemplate(new PromptTemplate(ragPrompt))
                        .allowEmptyContext(true)
                        .build())
                // After memory and before the tool loop, so retrieval runs once per question.
                .order(ToolCallingAdvisor.DEFAULT_ORDER - 1)
                .build();
    }

    static class VectorStoreFilePresent implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String path = context.getEnvironment().getProperty("rag.store-file", "../data/vector-store.json");
            return new File(path).isFile();
        }

    }

}
