package vn.glassliving.room.service;

import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private static final Set<String> DEMO_ROOM_CODES = Set.of(
            "GT-B-502",
            "LH-303",
            "RS-101",
            "RS-205"
    );
    private static final Set<String> DEMO_ROOM_SLUGS = Set.of(
            "penthouse-glass-tower-502",
            "penthouse-smartrent-tower-502",
            "studio-lotus-303",
            "riverside-q7-101",
            "riverside-q7-205"
    );

    @Transactional(readOnly = true)
    public Page<Room> search(String district, String type, BigDecimal minPrice, BigDecimal maxPrice,
                             Boolean petAllowed, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        Room.RoomType roomType = parseRoomType(type);
        return roomRepository.search(Room.RoomStatus.AVAILABLE,
                emptyToNull(district), roomType, minPrice, maxPrice, petAllowed, pageable);
    }

    @Transactional(readOnly = true)
    public List<Room> searchCustomer(String query,
                                     UUID propertyId,
                                     String type,
                                     BigDecimal minPrice,
                                     BigDecimal maxPrice,
                                     BigDecimal minArea,
                                     BigDecimal maxArea,
                                     String amenity,
                                     int limit) {
        String cleanQuery = emptyToNull(query);
        String cleanAmenity = emptyToNull(amenity);
        Room.RoomType roomType = parseRoomType(type);

        Specification<Room> spec = (root, criteriaQuery, cb) ->
                cb.equal(root.get("status"), Room.RoomStatus.AVAILABLE);

        if (propertyId != null) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.equal(root.get("propertyId"), propertyId));
        }
        if (roomType != null) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.equal(root.get("type"), roomType));
        }
        if (minPrice != null) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.greaterThanOrEqualTo(root.get("priceMonthly"), minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.lessThanOrEqualTo(root.get("priceMonthly"), maxPrice));
        }
        if (minArea != null) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.greaterThanOrEqualTo(root.get("areaSqm"), minArea));
        }
        if (maxArea != null) {
            spec = spec.and((root, criteriaQuery, cb) -> cb.lessThanOrEqualTo(root.get("areaSqm"), maxArea));
        }
        if (cleanQuery != null) {
            String pattern = "%" + cleanQuery.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, criteriaQuery, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("addressLine")), pattern),
                    cb.like(cb.lower(root.get("district")), pattern),
                    cb.like(cb.lower(root.get("city")), pattern)
            ));
        }
        if (cleanAmenity != null) {
            String normalized = cleanAmenity.toUpperCase(Locale.ROOT);
            if ("PET".equals(normalized)) {
                spec = spec.and((root, criteriaQuery, cb) -> cb.isTrue(root.get("petAllowed")));
            } else if ("BALCONY".equals(normalized)) {
                spec = spec.and((root, criteriaQuery, cb) -> cb.isTrue(root.get("hasBalcony")));
            } else {
                String pattern = "%" + cleanAmenity.toLowerCase(Locale.ROOT) + "%";
                spec = spec.and((root, criteriaQuery, cb) -> {
                    criteriaQuery.distinct(true);
                    var amenityJoin = root.join("amenities", jakarta.persistence.criteria.JoinType.LEFT);
                    return cb.or(
                            cb.like(cb.lower(amenityJoin.get("code")), pattern),
                            cb.like(cb.lower(amenityJoin.get("name")), pattern)
                    );
                });
            }
        }

        List<Room> rooms = roomRepository.findAll(
                spec,
                PageRequest.of(0, Math.max(1, Math.min(limit, 100)),
                        Sort.by(Sort.Direction.DESC, "publishedAt"))
        ).getContent();
        rooms = rooms.stream()
                .filter(room -> !isDemoCatalogRoom(room))
                .toList();
        rooms.forEach(room -> Hibernate.initialize(room.getAmenities()));
        return rooms;
    }

    @Transactional(readOnly = true)
    public Room getBySlug(String slug) {
        Room r = roomRepository.findBySlug(slug).orElseThrow(() -> BusinessException.notFound("Phòng"));
        // Force-load lazy collections while transaction is open — Thymeleaf iterates them later.
        Hibernate.initialize(r.getAmenities());
        return r;
    }

    @Transactional(readOnly = true)
    public Room getById(UUID id) {
        Room r = roomRepository.findById(id).orElseThrow(() -> BusinessException.notFound("Phòng"));
        Hibernate.initialize(r.getAmenities());
        return r;
    }

    @Transactional(readOnly = true)
    public List<Room> findSimilar(Room source, int limit) {
        return roomRepository.findSimilar(source.getId(), source.getDistrict(), PageRequest.of(0, limit));
    }

    @Transactional
    public void incrementViewCount(UUID id) {
        roomRepository.findById(id).ifPresent(r -> r.setViewCount(r.getViewCount() + 1));
    }

    @Transactional(readOnly = true)
    public Page<Room> listForOwner(UUID ownerId, int page, int size) {
        return roomRepository.findByOwnerId(ownerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "code")));
    }

    private static Room.RoomType parseRoomType(String value) {
        String clean = emptyToNull(value);
        if (clean == null) return null;
        try {
            return Room.RoomType.valueOf(clean.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static boolean isDemoCatalogRoom(Room room) {
        if (room == null) return false;
        String code = emptyToNull(room.getCode());
        String slug = emptyToNull(room.getSlug());
        return (code != null && DEMO_ROOM_CODES.contains(code.toUpperCase(Locale.ROOT)))
                || (slug != null && DEMO_ROOM_SLUGS.contains(slug.toLowerCase(Locale.ROOT)));
    }

    private static String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
}
