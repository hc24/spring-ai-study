package com.hc24.springaidemo.indexer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class RagIndexRunner implements CommandLineRunner {

    private final EmbeddingModel embeddingModel;
    private final String storeFile;

    public RagIndexRunner(EmbeddingModel embeddingModel, @Value("${rag.store-file}") String storeFile) {
        this.embeddingModel = embeddingModel;
        this.storeFile = storeFile;
    }

    @Override
    public void run(String... args) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath:rag/*.txt");
        List<Document> documents = new ArrayList<>();
        for (Resource resource : resources) {
            TextReader reader = new TextReader(resource);
            reader.getCustomMetadata().put("source", resource.getFilename());
            documents.addAll(reader.read());
        }
        SimpleVectorStore store = SimpleVectorStore.builder(this.embeddingModel).build();
        store.add(TokenTextSplitter.builder().build().apply(documents));
        File output = new File(this.storeFile);
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        store.save(output);
        System.out.println("Wrote " + documents.size() + " documents to " + output.getAbsolutePath());
    }

}
