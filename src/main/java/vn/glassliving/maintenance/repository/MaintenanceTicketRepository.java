package vn.glassliving.maintenance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.glassliving.maintenance.entity.MaintenanceTicket;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface MaintenanceTicketRepository extends JpaRepository<MaintenanceTicket, UUID> {

    Page<MaintenanceTicket> findByOwnerIdOrderByReportedAtDesc(UUID ownerId, Pageable pageable);

    Page<MaintenanceTicket> findByOwnerIdAndStatusOrderByReportedAtDesc(
            UUID ownerId, MaintenanceTicket.Status status, Pageable pageable);

    Page<MaintenanceTicket> findByOwnerIdAndPriorityOrderByReportedAtDesc(
            UUID ownerId, MaintenanceTicket.Priority priority, Pageable pageable);

    List<MaintenanceTicket> findTop8ByReporterUserIdOrderByReportedAtDesc(UUID reporterUserId);

    long countByOwnerIdAndStatus(UUID ownerId, MaintenanceTicket.Status status);

    long countByOwnerId(UUID ownerId);

    long countByReporterUserId(UUID reporterUserId);

    long countByReporterUserIdAndStatusIn(UUID reporterUserId, List<MaintenanceTicket.Status> statuses);

    @Query("""
            SELECT t FROM MaintenanceTicket t
            WHERE t.ownerId = :ownerId
              AND t.reportedAt >= :from
              AND t.reportedAt < :to
            ORDER BY t.reportedAt ASC
            """)
    List<MaintenanceTicket> findForReport(@Param("ownerId") UUID ownerId,
                                          @Param("from") OffsetDateTime from,
                                          @Param("to") OffsetDateTime to);
}
