package vn.glassliving.room.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.glassliving.room.entity.Room;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID>, JpaSpecificationExecutor<Room> {

    Optional<Room> findBySlug(String slug);

    Page<Room> findByStatus(Room.RoomStatus status, Pageable pageable);

    @Query("""
            SELECT r FROM Room r
            WHERE r.status = :status
              AND (:district IS NULL OR r.district = :district)
              AND (:type IS NULL OR r.type = :type)
              AND (:minPrice IS NULL OR r.priceMonthly >= :minPrice)
              AND (:maxPrice IS NULL OR r.priceMonthly <= :maxPrice)
              AND (:petAllowed IS NULL OR r.petAllowed = :petAllowed)
            """)
    Page<Room> search(@Param("status") Room.RoomStatus status,
                      @Param("district") String district,
                      @Param("type") Room.RoomType type,
                      @Param("minPrice") BigDecimal minPrice,
                      @Param("maxPrice") BigDecimal maxPrice,
                      @Param("petAllowed") Boolean petAllowed,
                      Pageable pageable);

    List<Room> findByPropertyIdOrderByCodeAsc(UUID propertyId);

    Page<Room> findByOwnerId(UUID ownerId, Pageable pageable);

    List<Room> findByOwnerIdAndCurrentTenantId(UUID ownerId, UUID currentTenantId);

    List<Room> findByCurrentTenantId(UUID currentTenantId);

    @Query("SELECT r FROM Room r WHERE r.id <> :excludeId AND r.district = :district AND r.status = 'AVAILABLE' ORDER BY r.ratingAvg DESC")
    List<Room> findSimilar(@Param("excludeId") UUID excludeId,
                           @Param("district") String district,
                           Pageable pageable);

    long countByOwnerIdAndStatus(UUID ownerId, Room.RoomStatus status);

    boolean existsByPropertyIdAndCodeIgnoreCase(UUID propertyId, String code);

    Optional<Room> findByPropertyIdAndCodeIgnoreCase(UUID propertyId, String code);
}
