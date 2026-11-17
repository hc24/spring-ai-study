package com.hc24.springaidemo.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * File mode excludes Spring's DataSource auto-configuration so startup does not
 * open PostgreSQL. This turns that same auto-configuration back on for pgvector
 * and leaves the connection in spring.datasource.*.
 */
@Configuration
@ConditionalOnProperty(name = "rag.store", havingValue = "pgvector")
@Import(DataSourceAutoConfiguration.class)
public class PgVectorDataSourceConfiguration {
}
