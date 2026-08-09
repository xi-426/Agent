package com.yan.agent.document;

import java.util.Arrays;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentVectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentVectorRepository(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveEmbedding(
            Long chunkId,
            float[] embedding) {
        if (chunkId == null) {
            throw new IllegalArgumentException(
                    "切片ID不能为空");
        }

        if (embedding == null
                || embedding.length != 1024) {
            throw new IllegalArgumentException(
                    "向量必须是1024维");
        }

        String vectorText = Arrays.toString(embedding);

        // ::vector：PostgreSQL 类型转换，把字符串转成 pgvector 向量。
        String sql = "UPDATE document_chunk "
                + "SET embedding = ?::vector "
                + "WHERE id = ?";

        int updatedRows = jdbcTemplate.update(
                sql,
                vectorText,
                chunkId);

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "向量保存失败，切片ID："
                            + chunkId);
        }
    }

    public List<RetrievedChunk> searchSimilar(
            Long knowledgeBaseId,
            float[] queryEmbedding,
            int topK) {
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException(
                    "知识库ID不能为空");
        }

        if (queryEmbedding == null
                || queryEmbedding.length != 1024) {
            throw new IllegalArgumentException(
                    "问题向量必须是1024维");
        }

        if (topK < 1 || topK > 20) {
            throw new IllegalArgumentException(
                    "topK必须在1到20之间");
        }

        String vectorText = Arrays.toString(queryEmbedding);

        String sql = "SELECT "
                + "dc.id AS chunk_id, "
                + "dc.document_id, "
                + "d.original_name AS document_name, "
                + "dc.chunk_index, "
                + "dc.content, "
                + "dc.embedding <=> ?::vector AS distance "
                + "FROM document_chunk dc "
                + "JOIN document d "
                + "ON d.id = dc.document_id "
                + "WHERE d.knowledge_base_id = ? "
                + "AND dc.embedding IS NOT NULL "
                + "ORDER BY distance "
                + "LIMIT ?";

        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> new RetrievedChunk(
                        resultSet.getLong(
                                "chunk_id"),
                        resultSet.getLong(
                                "document_id"),
                        resultSet.getString(
                                "document_name"),
                        resultSet.getInt(
                                "chunk_index"),
                        resultSet.getString(
                                "content"),
                        resultSet.getDouble(
                                "distance")),
                vectorText,
                knowledgeBaseId,
                topK);
    }
}
