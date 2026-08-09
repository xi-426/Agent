package com.yan.agent.document;

import java.util.List;

public class RagResult {

    private final String answer;
    private final List<RetrievedChunk> sources;

    public RagResult(
            String answer,
            List<RetrievedChunk> sources) {
        this.answer = answer;
        this.sources = sources;
    }

    public String getAnswer() {
        return answer;
    }

    public List<RetrievedChunk> getSources() {
        return sources;
    }
}
