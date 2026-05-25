package vn.glassliving.room.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.room.entity.RoomImage;

import java.util.List;
import java.util.UUID;

public interface RoomImageRepository extends JpaRepository<RoomImage, UUID> {
    List<RoomImage> findByRoomIdOrderBySortOrderAsc(UUID roomId);
}
