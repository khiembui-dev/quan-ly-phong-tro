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
    List<Invoice> findByTenantUserIdOrderByIssueDateDescCreatedAtDesc(UUID tenantUserId);
    List<Invoice> findByStatusIn(Collection<Invoice.InvoiceStatus> statuses);
    List<Invoice> findByStatusInAndDueDateLessThanEqual(Collection<Invoice.InvoiceStatus> statuses, LocalDate dueDate);
    List<Invoice> findByStatusAndDueDateBefore(Invoice.InvoiceStatus status, LocalDate dueDate);
    List<Invoice> findByOwnerIdAndStatusInAndDueDateLessThanEqual(UUID ownerId,
                                                                  Collection<Invoice.InvoiceStatus> statuses,
                                                                  LocalDate dueDate);
    List<Invoice> findByOwnerIdAndStatusAndDueDateBefore(UUID ownerId,
                                                         Invoice.InvoiceStatus status,
                                                         LocalDate dueDate);
    List<Invoice> findByOwnerIdAndRoomIdInAndStatusInAndDueDateBeforeOrderByDueDateAsc(
            UUID ownerId,
            Collection<UUID> roomIds,
            Collection<Invoice.InvoiceStatus> statuses,
            LocalDate dueDate);
    Optional<Invoice> findFirstByOwnerIdAndRoomIdAndStatusInAndDueDateBeforeOrderByDueDateAsc(
            UUID ownerId,
            UUID roomId,
            Collection<Invoice.InvoiceStatus> statuses,
            LocalDate dueDate);
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

    long countByOwnerId(UUID ownerId);

    @Query(value = """
            SELECT i.*
            FROM invoice i
            LEFT JOIN room r ON r.id = i.room_id AND r.deleted = false
            LEFT JOIN property p ON p.id = r.property_id AND p.deleted = false
            LEFT JOIN app_user u ON u.id = i.tenant_user_id AND u.deleted = false
            WHERE i.deleted = false
              AND i.owner_id = :ownerId
              AND (:status = '' OR i.status = :status)
              AND (:propertyId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR r.property_id = :propertyId)
              AND (:roomId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR i.room_id = :roomId)
              AND (:month = 0 OR i.period_month = :month)
              AND (:year = 0 OR i.period_year = :year)
              AND i.issue_date >= :fromDate
              AND i.issue_date <= :toDate
              AND (
                    :q = ''
                    OR LOWER(i.code) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(r.code, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(r.title, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(p.name, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(u.full_name, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(u.email, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(u.phone, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR CONCAT(i.period_month, '/', i.period_year) LIKE CONCAT('%', :q, '%')
              )
            ORDER BY i.issue_date DESC, i.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM invoice i
            LEFT JOIN room r ON r.id = i.room_id AND r.deleted = false
            LEFT JOIN property p ON p.id = r.property_id AND p.deleted = false
            LEFT JOIN app_user u ON u.id = i.tenant_user_id AND u.deleted = false
            WHERE i.deleted = false
              AND i.owner_id = :ownerId
              AND (:status = '' OR i.status = :status)
              AND (:propertyId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR r.property_id = :propertyId)
              AND (:roomId = CAST('00000000-0000-0000-0000-000000000000' AS uuid) OR i.room_id = :roomId)
              AND (:month = 0 OR i.period_month = :month)
              AND (:year = 0 OR i.period_year = :year)
              AND i.issue_date >= :fromDate
              AND i.issue_date <= :toDate
              AND (
                    :q = ''
                    OR LOWER(i.code) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(r.code, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(r.title, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(p.name, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(u.full_name, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(u.email, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR LOWER(COALESCE(u.phone, '')) LIKE CONCAT('%', LOWER(:q), '%')
                    OR CONCAT(i.period_month, '/', i.period_year) LIKE CONCAT('%', :q, '%')
              )
            """,
            nativeQuery = true)
    Page<Invoice> searchByOwnerId(@Param("ownerId") UUID ownerId,
                                  @Param("status") String status,
                                  @Param("q") String q,
                                  @Param("propertyId") UUID propertyId,
                                  @Param("month") Short month,
                                  @Param("year") Short year,
                                  @Param("fromDate") LocalDate fromDate,
                                  @Param("toDate") LocalDate toDate,
                                  @Param("roomId") UUID roomId,
                                  Pageable pageable);

    @Query(value = """
            SELECT i.*
            FROM invoice i
            WHERE i.deleted = false
              AND i.tenant_user_id = :tenantUserId
              AND (:status = '' OR i.status = :status)
              AND (:month = 0 OR i.period_month = :month)
              AND (:year = 0 OR i.period_year = :year)
            ORDER BY i.issue_date DESC, i.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM invoice i
            WHERE i.deleted = false
              AND i.tenant_user_id = :tenantUserId
              AND (:status = '' OR i.status = :status)
              AND (:month = 0 OR i.period_month = :month)
              AND (:year = 0 OR i.period_year = :year)
            """,
            nativeQuery = true)
    Page<Invoice> searchByTenantUserId(@Param("tenantUserId") UUID tenantUserId,
                                       @Param("status") String status,
                                       @Param("month") Short month,
                                       @Param("year") Short year,
                                       Pageable pageable);

    List<Invoice> findByOwnerIdAndStatusInAndDueDate(UUID ownerId,
                                                     Collection<Invoice.InvoiceStatus> statuses,
                                                     LocalDate dueDate);

    long countByOwnerIdAndStatusInAndDueDateBetween(UUID ownerId,
                                                    Collection<Invoice.InvoiceStatus> statuses,
                                                    LocalDate startDate,
                                                    LocalDate endDate);

    boolean existsByContractIdAndPeriodYearAndPeriodMonth(UUID contractId, Short periodYear, Short periodMonth);

    boolean existsByCode(String code);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(code FROM 7) AS INTEGER)), 0)
            FROM invoice
            WHERE code ~ '^PT-HD-[0-9]+$'
            """, nativeQuery = true)
    int findMaxSequentialInvoiceCode();
}
