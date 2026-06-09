package vn.glassliving.maintenance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.maintenance.entity.MaintenanceTicketMessage;

import java.util.List;
import java.util.UUID;

public interface MaintenanceTicketMessageRepository extends JpaRepository<MaintenanceTicketMessage, UUID> {
    List<MaintenanceTicketMessage> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
    List<MaintenanceTicketMessage> findByTicketIdInOrderByCreatedAtAsc(List<UUID> ticketIds);
}
