package vn.glassliving.contract.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.contract.entity.ContractImage;

import java.util.List;
import java.util.UUID;

public interface ContractImageRepository extends JpaRepository<ContractImage, UUID> {
    List<ContractImage> findByContractIdOrderBySortOrderAsc(UUID contractId);
}
