package com.yan.agent.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseRepository
        extends JpaRepository<KnowledgeBase, Long> {

    Optional<KnowledgeBase> findByIdAndOwnerId(
            Long id,
            Long ownerId);

    List<KnowledgeBase> findByOwnerIdOrderByIdDesc(Long ownerId);
}
