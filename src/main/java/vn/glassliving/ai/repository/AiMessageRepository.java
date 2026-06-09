package vn.glassliving.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.ai.entity.AiMessage;

import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends JpaRepository<AiMessage, UUID> {
    List<AiMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
    List<AiMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
