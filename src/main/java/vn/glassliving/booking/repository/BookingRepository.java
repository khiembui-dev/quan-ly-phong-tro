package vn.glassliving.booking.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.booking.entity.Booking;

import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Page<Booking> findByTenantUserId(UUID tenantUserId, Pageable pageable);
    Page<Booking> findByOwnerId(UUID ownerId, Pageable pageable);
}
