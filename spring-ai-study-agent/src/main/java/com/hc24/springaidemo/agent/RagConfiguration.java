package com.hc24.springaidemo.agent;

import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "app.rag-enabled", havingValue = "true", matchIfMissing = true)
public class RagConfiguration {

    @Bean
    VectorStore ragVectorStore(EmbeddingModel embeddingModel) throws IOException {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:rag/*.txt");
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resources) {
            TextReader reader = new TextReader(resource);
            reader.getCustomMetadata().put("source", resource.getFilename());
            documents.addAll(reader.read());
        }
        store.add(TokenTextSplitter.builder().build().apply(documents));
        return store;
    }

    @Bean
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

}
