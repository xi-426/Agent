ALTER TABLE document_chunk
    ADD COLUMN embedding vector(1024);

CREATE INDEX idx_document_chunk_embedding_hnsw
    ON document_chunk
    USING hnsw (embedding vector_cosine_ops);
