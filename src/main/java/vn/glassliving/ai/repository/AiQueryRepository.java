package vn.glassliving.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.ai.entity.AiQuery;

import java.util.List;
import java.util.UUID;

public interface AiQueryRepository extends JpaRepository<AiQuery, UUID> {
    List<AiQuery> findTop10ByUserIdOrderByCreatedAtDesc(UUID userId);
}
