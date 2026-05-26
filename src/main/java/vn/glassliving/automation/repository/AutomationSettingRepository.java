package vn.glassliving.automation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.glassliving.automation.entity.AutomationSetting;

import java.util.Optional;
import java.util.UUID;

public interface AutomationSettingRepository extends JpaRepository<AutomationSetting, UUID> {
    Optional<AutomationSetting> findByOwnerId(UUID ownerId);
    Optional<AutomationSetting> findFirstByContactEmailIsNotNullOrContactZaloIsNotNullOrderByUpdatedAtDesc();
}
