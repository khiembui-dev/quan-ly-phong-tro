package vn.glassliving.contract.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.contract.entity.Contract;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractRepository extends JpaRepository<Contract, UUID> {
    Page<Contract> findByOwnerId(UUID ownerId, Pageable pageable);
    Page<Contract> findByOwnerIdAndStatus(UUID ownerId, Contract.ContractStatus status, Pageable pageable);
    Page<Contract> findByTenantUserId(UUID tenantUserId, Pageable pageable);
    List<Contract> findByOwnerIdAndTenantUserIdOrderByStartDateDesc(UUID ownerId, UUID tenantUserId);
    Optional<Contract> findFirstByOwnerIdAndRoomIdAndTenantUserIdAndStatusOrderByStartDateDesc(
            UUID ownerId, UUID roomId, UUID tenantUserId, Contract.ContractStatus status);
    boolean existsByCode(String code);
    long countByOwnerIdAndStatus(UUID ownerId, Contract.ContractStatus status);
    List<Contract> findByEndDateBeforeAndStatus(LocalDate before, Contract.ContractStatus status);
    List<Contract> findByOwnerIdAndStatusAndEndDate(UUID ownerId, Contract.ContractStatus status, LocalDate endDate);
    long countByOwnerIdAndStatusAndEndDateBetween(UUID ownerId, Contract.ContractStatus status,
                                                  LocalDate startDate, LocalDate endDate);
}
