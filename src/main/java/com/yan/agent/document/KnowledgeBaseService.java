package com.yan.agent.document;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    public KnowledgeBase create(
            Long ownerId,
            String name,
            String description) {

        String cleanedDescription = description == null
                ? null
                : description.trim();

        KnowledgeBase knowledgeBase = new KnowledgeBase(
                ownerId,
                name.trim(),
                cleanedDescription);

        return repository.save(knowledgeBase);
    }

    public KnowledgeBase requireOwnedBy(
            Long knowledgeBaseId,
            Long ownerId) {

        return repository.findByIdAndOwnerId(
                knowledgeBaseId,
                ownerId)
                .orElseThrow(() -> new KnowledgeBaseNotFoundException(
                        knowledgeBaseId));
    }

    public List<KnowledgeBase> findOwnedBy(Long ownerId) {
        return repository.findByOwnerIdOrderByIdDesc(ownerId);
    }
}
