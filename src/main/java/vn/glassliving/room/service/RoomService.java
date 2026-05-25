package vn.glassliving.room.service;

import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    @Transactional(readOnly = true)
    public Page<Room> search(String district, String type, BigDecimal minPrice, BigDecimal maxPrice,
                             Boolean petAllowed, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        Room.RoomType roomType = type != null && !type.isBlank() ? Room.RoomType.valueOf(type) : null;
        return roomRepository.search(Room.RoomStatus.AVAILABLE,
                emptyToNull(district), roomType, minPrice, maxPrice, petAllowed, pageable);
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

    private static String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
}
