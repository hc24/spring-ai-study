package com.hc24.springaidemo.indexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IndexerApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(IndexerApplication.class, args)));
    }

}
