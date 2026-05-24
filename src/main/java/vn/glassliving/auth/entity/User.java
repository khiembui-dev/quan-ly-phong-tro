package vn.glassliving.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import vn.glassliving.common.audit.BaseEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "idx_user_status", columnList = "status"),
        @Index(name = "idx_user_email_lc", columnList = "email")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE app_user SET deleted = true, updated_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted = false")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone", length = 20, unique = true)
    private String phone;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "gender", length = 16)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(name = "identity_type", length = 16)
    @Enumerated(EnumType.STRING)
    private IdentityType identityType;

    @Column(name = "identity_number", length = 32)
    private String identityNumber;

    @Column(name = "identity_issued_date")
    private LocalDate identityIssuedDate;

    @Column(name = "identity_issued_place", length = 160)
    private String identityIssuedPlace;

    @Column(name = "identity_front_url", columnDefinition = "TEXT")
    private String identityFrontUrl;

    @Column(name = "identity_back_url", columnDefinition = "TEXT")
    private String identityBackUrl;

    @Column(name = "identity_verified", nullable = false)
    @Builder.Default
    private boolean identityVerified = false;

    @Column(name = "identity_updated_at")
    private OffsetDateTime identityUpdatedAt;

    @Column(name = "identity_verified_at")
    private OffsetDateTime identityVerifiedAt;

    @Column(name = "identity_verified_by")
    private java.util.UUID identityVerifiedBy;

    @Column(name = "locale", nullable = false, length = 8)
    @Builder.Default
    private String locale = "vi-VN";

    @Column(name = "status", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "two_factor_secret", length = 64)
    private String twoFactorSecret;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 16)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    public boolean hasRole(Role r) {
        return roles != null && roles.contains(r);
    }

    public enum Gender { MALE, FEMALE, OTHER }
    public enum IdentityType { CCCD, CMND, PASSPORT }
    public enum UserStatus { ACTIVE, SUSPENDED, CLOSED, PENDING_VERIFICATION }
    public enum Role { TENANT, OWNER, ADMIN, STAFF }
}
