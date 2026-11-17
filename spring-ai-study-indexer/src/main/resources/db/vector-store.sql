-- Run this statement while connected to an existing maintenance database.
-- CREATE DATABASE cannot run in the same session as the statements below.
CREATE DATABASE springaistudy;

-- Connect to springaistudy, then run the rest.
-- Matches Spring AI 2.0.1 PgVectorStore defaults: public.vector_store, uuid id, cosine HNSW.
-- embedding width must match kinfra-text-embedding-0.6b and spring.ai.vectorstore.pgvector.dimensions.
CREATE
EXTENSION IF NOT EXISTS vector;
CREATE
EXTENSION IF NOT EXISTS hstore;
CREATE
EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS public.vector_store
(
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content  text,
    metadata json,
    embedding vector(1024)
);

CREATE INDEX IF NOT EXISTS spring_ai_vector_index
    ON public.vector_store
    USING hnsw (embedding vector_cosine_ops);
