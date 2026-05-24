package vn.glassliving.utility.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.glassliving.utility.entity.UtilityReading;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UtilityReadingRepository extends JpaRepository<UtilityReading, UUID> {

    Page<UtilityReading> findByOwnerIdOrderByPeriodYearDescPeriodMonthDescReadingDateDesc(UUID ownerId, Pageable pageable);

    Page<UtilityReading> findByOwnerIdAndPropertyIdOrderByPeriodYearDescPeriodMonthDescReadingDateDesc(
            UUID ownerId, UUID propertyId, Pageable pageable);

    Page<UtilityReading> findByRoomIdOrderByPeriodYearDescPeriodMonthDesc(UUID roomId, Pageable pageable);

    Optional<UtilityReading> findFirstByRoomIdOrderByPeriodYearDescPeriodMonthDesc(UUID roomId);

    Optional<UtilityReading> findByRoomIdAndPeriodYearAndPeriodMonth(UUID roomId, Short year, Short month);

    List<UtilityReading> findByOwnerIdAndPeriodYearAndPeriodMonth(UUID ownerId, Short year, Short month);

    Optional<UtilityReading> findByOwnerIdAndRoomIdAndPeriodYearAndPeriodMonth(UUID ownerId, UUID roomId, Short year, Short month);

    @Query("""
            SELECT u FROM UtilityReading u
            WHERE u.roomId = :roomId
              AND (u.periodYear < :year OR (u.periodYear = :year AND u.periodMonth < :month))
            ORDER BY u.periodYear DESC, u.periodMonth DESC
            """)
    List<UtilityReading> findPreviousReadings(@Param("roomId") UUID roomId,
                                              @Param("year") Short year,
                                              @Param("month") Short month,
                                              org.springframework.data.domain.Pageable pageable);

    long countByOwnerId(UUID ownerId);

    @Query("SELECT COUNT(u) FROM UtilityReading u WHERE u.ownerId = :ownerId AND u.periodYear = :year AND u.periodMonth = :month")
    long countByOwnerAndPeriod(@Param("ownerId") UUID ownerId, @Param("year") Short year, @Param("month") Short month);

    @Query("""
            SELECT u FROM UtilityReading u
            WHERE u.ownerId = :ownerId
              AND (u.periodYear > :fromYear OR (u.periodYear = :fromYear AND u.periodMonth >= :fromMonth))
              AND (u.periodYear < :toYear OR (u.periodYear = :toYear AND u.periodMonth <= :toMonth))
            ORDER BY u.periodYear ASC, u.periodMonth ASC, u.readingDate ASC
            """)
    List<UtilityReading> findForReport(@Param("ownerId") UUID ownerId,
                                       @Param("fromYear") Short fromYear,
                                       @Param("fromMonth") Short fromMonth,
                                       @Param("toYear") Short toYear,
                                       @Param("toMonth") Short toMonth);
}
