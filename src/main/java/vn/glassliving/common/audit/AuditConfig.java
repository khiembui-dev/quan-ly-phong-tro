package vn.glassliving.common.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.glassliving.auth.security.AppUserDetails;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.UUID;

@Configuration
public class AuditConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of((TemporalAccessor) OffsetDateTime.now());
    }

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return Optional.empty();
            Object principal = auth.getPrincipal();
            if (principal instanceof AppUserDetails u) {
                return Optional.of(u.getId());
            }
            return Optional.empty();
        };
    }
}
