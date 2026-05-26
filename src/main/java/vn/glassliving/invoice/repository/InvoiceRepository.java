package vn.glassliving.invoice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.glassliving.invoice.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Page<Invoice> findByOwnerId(UUID ownerId, Pageable pageable);
    Page<Invoice> findByOwnerIdAndStatus(UUID ownerId, Invoice.InvoiceStatus status, Pageable pageable);
    Page<Invoice> findByTenantUserId(UUID tenantUserId, Pageable pageable);
    List<Invoice> findTop5ByTenantUserIdOrderByIssueDateDesc(UUID tenantUserId);
    List<Invoice> findTop18ByOwnerIdAndRoomIdOrderByPeriodYearDescPeriodMonthDescCreatedAtDesc(UUID ownerId, UUID roomId);
    List<Invoice> findByOwnerIdAndRoomIdAndTenantUserIdAndStatusOrderByPeriodYearDescPeriodMonthDescCreatedAtDesc(
            UUID ownerId, UUID roomId, UUID tenantUserId, Invoice.InvoiceStatus status);
    Optional<Invoice> findFirstByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonthOrderByCreatedAtDesc(
            UUID ownerId, UUID roomId, Short periodYear, Short periodMonth);
    Optional<Invoice> findFirstByOwnerIdAndContractIdAndPeriodYearAndPeriodMonthOrderByCreatedAtDesc(
            UUID ownerId, UUID contractId, Short periodYear, Short periodMonth);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.ownerId = :ownerId
              AND (i.periodYear > :fromYear OR (i.periodYear = :fromYear AND i.periodMonth >= :fromMonth))
              AND (i.periodYear < :toYear OR (i.periodYear = :toYear AND i.periodMonth <= :toMonth))
            ORDER BY i.periodYear ASC, i.periodMonth ASC, i.createdAt ASC
            """)
    List<Invoice> findForReport(@Param("ownerId") UUID ownerId,
                                @Param("fromYear") Short fromYear,
                                @Param("fromMonth") Short fromMonth,
                                @Param("toYear") Short toYear,
                                @Param("toMonth") Short toMonth);

    @Query(value = """
            SELECT *
            FROM invoice
            WHERE owner_id = :ownerId
              AND contract_id = :contractId
              AND period_year = :periodYear
              AND period_month = :periodMonth
            ORDER BY created_at DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Invoice> findAnyByOwnerIdAndContractIdAndPeriod(
            @Param("ownerId") UUID ownerId,
            @Param("contractId") UUID contractId,
            @Param("periodYear") Short periodYear,
            @Param("periodMonth") Short periodMonth);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.ownerId = :ownerId AND i.status = :status")
    BigDecimal sumTotalByOwnerAndStatus(@Param("ownerId") UUID ownerId,
                                        @Param("status") Invoice.InvoiceStatus status);

    long countByOwnerIdAndStatus(UUID ownerId, Invoice.InvoiceStatus status);

    List<Invoice> findByOwnerIdAndStatusInAndDueDate(UUID ownerId,
                                                     Collection<Invoice.InvoiceStatus> statuses,
                                                     LocalDate dueDate);

    long countByOwnerIdAndStatusInAndDueDateBetween(UUID ownerId,
                                                    Collection<Invoice.InvoiceStatus> statuses,
                                                    LocalDate startDate,
                                                    LocalDate endDate);

    boolean existsByContractIdAndPeriodYearAndPeriodMonth(UUID contractId, Short periodYear, Short periodMonth);
}
