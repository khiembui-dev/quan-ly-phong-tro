package vn.glassliving.favorite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.favorite.entity.Favorite;
import vn.glassliving.favorite.repository.FavoriteRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;

    @Transactional
    public boolean toggle(UUID userId, UUID roomId) {
        if (favoriteRepository.existsByUserIdAndRoomId(userId, roomId)) {
            favoriteRepository.deleteByUserIdAndRoomId(userId, roomId);
            return false;
        }
        favoriteRepository.save(Favorite.builder().userId(userId).roomId(roomId).build());
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(UUID userId, UUID roomId) {
        return favoriteRepository.existsByUserIdAndRoomId(userId, roomId);
    }

    @Transactional(readOnly = true)
    public long count(UUID userId) {
        return favoriteRepository.countByUserId(userId);
    }
}
