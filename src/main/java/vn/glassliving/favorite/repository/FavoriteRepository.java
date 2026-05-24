package vn.glassliving.favorite.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.favorite.entity.Favorite;

import java.util.List;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, Favorite.PK> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByUserIdAndRoomId(UUID userId, UUID roomId);
    void deleteByUserIdAndRoomId(UUID userId, UUID roomId);
    long countByUserId(UUID userId);
}
