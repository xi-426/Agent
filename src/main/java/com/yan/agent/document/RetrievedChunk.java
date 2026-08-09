package com.yan.agent.document;

public class RetrievedChunk {

    private final Long chunkId;
    private final Long documentId;
    private final String documentName;
    private final int chunkIndex;
    private final String content;
    private final double distance;

    public RetrievedChunk(
            Long chunkId,
            Long documentId,
            String documentName,
            int chunkIndex,
            String content,
            double distance) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.distance = distance;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public double getDistance() {
        return distance;
    }
}
