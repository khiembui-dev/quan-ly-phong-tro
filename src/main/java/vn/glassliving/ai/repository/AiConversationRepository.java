package vn.glassliving.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.ai.entity.AiConversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiConversationRepository extends JpaRepository<AiConversation, UUID> {
    List<AiConversation> findTop20ByUserIdAndRoleOrderByUpdatedAtDesc(UUID userId, AiConversation.Role role);
    Optional<AiConversation> findByIdAndUserIdAndRole(UUID id, UUID userId, AiConversation.Role role);
}
