package vn.glassliving.admin.page.tenants;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.storage.LocalUploadService;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.notification.service.NotificationService;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.service.RoomAdminService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Controller
@RequestMapping("/admin/tenants")
@RequiredArgsConstructor
public class TenantsActionController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final RoomAdminService roomAdminService;
    private final LocalUploadService localUploadService;
    private final MessageSource messageSource;

    /**
     * Owner creates a tenant account directly with a chosen password.
     * The tenant can log in immediately with the email/password entered here.
     */
    @PostMapping("/create")
    @Transactional
    public String create(@AuthenticationPrincipal AppUserDetails me,
                         @RequestParam String email,
                         @RequestParam String fullName,
                         @RequestParam String phone,
                         @RequestParam String password,
                         Locale locale,
                         RedirectAttributes ra) {
        try {
            String emailNormalized = normalizeEmail(email, locale);
            String cleanName = clean(fullName);
            String cleanPhone = clean(phone);
            validateNewPassword(password, null, locale);
            if (cleanName == null) {
                throw BusinessException.badRequest(msg(locale, "tenants.validation.fullNameRequired"));
            }
            if (cleanPhone == null) {
                throw BusinessException.badRequest(msg(locale, "tenants.validation.phoneRequired"));
            }
            if (userRepository.existsByEmailIgnoreCase(emailNormalized)) {
                throw BusinessException.conflict(msg(locale, "tenants.detail.validation.emailInUse"));
            }
            if (userRepository.existsByPhone(cleanPhone)) {
                throw BusinessException.conflict(msg(locale, "tenants.detail.validation.phoneInUse"));
            }

            User u = User.builder()
                    .email(emailNormalized)
                    .emailVerified(true)
                    .phone(cleanPhone)
                    .phoneVerified(false)
                    .fullName(cleanName)
                    .passwordHash(passwordEncoder.encode(password.trim()))
                    .roles(Set.of(User.Role.TENANT))
                    .status(User.UserStatus.ACTIVE)
                    .build();
            u = userRepository.save(u);

            notificationService.create(me.getId(), "TENANT_CREATED",
                    "Đã tạo tài khoản khách " + u.getFullName(),
                    "Khách có thể đăng nhập bằng email và mật khẩu bạn vừa đặt.",
                    "/admin/tenants");

            FlashAlert.ok(ra, msg(locale, "tenants.detail.flash.created", u.getFullName()));
        } catch (BusinessException ex) {
            addCreateFormFlash(ra, fullName, email, phone, ex);
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/tenants";
    }

    private static void addCreateFormFlash(RedirectAttributes ra,
                                           String fullName,
                                           String email,
                                           String phone,
                                           BusinessException ex) {
        String message = ex.getMessage();
        ra.addFlashAttribute("createTenantOpen", true);
        ra.addFlashAttribute("createTenantFullName", clean(fullName));
        ra.addFlashAttribute("createTenantEmail", clean(email));
        ra.addFlashAttribute("createTenantPhone", clean(phone));
        ra.addFlashAttribute("createTenantErrorField", createErrorField(message));
        ra.addFlashAttribute("createTenantErrorMessage", message);
    }

    private static String createErrorField(String message) {
        String lower = Objects.toString(message, "").toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("email")) return "email";
        if (lower.contains("số điện thoại") || lower.contains("sdt") || lower.contains("phone")) return "phone";
        if (lower.contains("mật khẩu") || lower.contains("password")) return "password";
        if (lower.contains("họ tên") || lower.contains("họ và tên") || lower.contains("tên") || lower.contains("name")) return "fullName";
        return "form";
    }

    @PostMapping("/{tenantId}/password")
    @Transactional
    public String changePassword(@PathVariable UUID tenantId,
                                 @RequestParam String newPassword,
                                 @RequestParam(required = false) String confirmPassword,
                                 Locale locale,
                                 RedirectAttributes ra) {
        try {
            User tenant = loadTenant(tenantId, locale);
            validateNewPassword(newPassword, confirmPassword, locale);
            tenant.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
            userRepository.save(tenant);
            notificationService.create(tenant.getId(), "PASSWORD_CHANGED",
                    "Mật khẩu tài khoản đã được cập nhật",
                    "Admin vừa đổi mật khẩu đăng nhập cho tài khoản của bạn.",
                    "/me/profile");
            FlashAlert.ok(ra, msg(locale, "tenants.detail.flash.passwordChanged", tenant.getFullName()));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/tenants/" + tenantId + "#security";
    }

    @PostMapping("/{tenantId}/identity")
    @Transactional
    public String updateIdentity(@AuthenticationPrincipal AppUserDetails me,
                                 @PathVariable UUID tenantId,
                                 @RequestParam(required = false) String fullName,
                                 @RequestParam(required = false) String email,
                                 @RequestParam(required = false) String phone,
                                 @RequestParam(required = false) String identityType,
                                 @RequestParam(required = false) String identityNumber,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate identityIssuedDate,
                                 @RequestParam(required = false) String identityIssuedPlace,
                                 @RequestParam(required = false) String permanentAddress,
                                 @RequestParam(required = false) MultipartFile identityFront,
                                 @RequestParam(required = false) MultipartFile identityBack,
                                 @RequestParam(defaultValue = "false") boolean clearIdentityFront,
                                 @RequestParam(defaultValue = "false") boolean clearIdentityBack,
                                 @RequestParam(defaultValue = "false") boolean identityVerified,
                                 Locale locale,
                                 RedirectAttributes ra) {
        try {
            User tenant = loadTenant(tenantId, locale);

            updateTenantAccountFields(tenant, fullName, email, phone, locale);
            tenant.setIdentityType(parseIdentityType(identityType, locale));
            tenant.setIdentityNumber(clean(identityNumber));
            tenant.setIdentityIssuedDate(identityIssuedDate);
            tenant.setIdentityIssuedPlace(clean(identityIssuedPlace));
            tenant.setPermanentAddress(clean(permanentAddress));

            String folder = "tenant-docs/" + tenant.getId();
            String frontUrl = localUploadService.storeImage(identityFront, folder, "cccd-front");
            String backUrl = localUploadService.storeImage(identityBack, folder, "cccd-back");
            if (frontUrl != null) tenant.setIdentityFrontUrl(frontUrl);
            if (backUrl != null) tenant.setIdentityBackUrl(backUrl);
            if (clearIdentityFront && frontUrl == null) tenant.setIdentityFrontUrl(null);
            if (clearIdentityBack && backUrl == null) tenant.setIdentityBackUrl(null);

            tenant.setIdentityUpdatedAt(OffsetDateTime.now());
            tenant.setIdentityVerified(identityVerified);
            tenant.setIdentityVerifiedAt(identityVerified ? OffsetDateTime.now() : null);
            tenant.setIdentityVerifiedBy(identityVerified ? me.getId() : null);
            userRepository.save(tenant);

            notificationService.create(tenant.getId(), "PROFILE_VERIFIED",
                    identityVerified ? "Hồ sơ giấy tờ đã được xác minh" : "Hồ sơ giấy tờ đã được cập nhật",
                    identityVerified
                            ? "Admin đã xác nhận thông tin CCCD/CMND của bạn."
                            : "Admin đã cập nhật hồ sơ giấy tờ, vui lòng kiểm tra lại khi cần.",
                    "/me/profile?tab=docs");
            FlashAlert.ok(ra, identityVerified
                    ? msg(locale, "tenants.detail.flash.profileSavedVerified")
                    : msg(locale, "tenants.detail.flash.profileSaved"));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/tenants/" + tenantId + "#identity";
    }

    private void updateTenantAccountFields(User tenant, String fullName, String email, String phone, Locale locale) {
        String cleanName = clean(fullName);
        if (cleanName == null) {
            throw BusinessException.badRequest(msg(locale, "tenants.validation.fullNameRequired"));
        }

        String emailNormalized = normalizeEmail(email, locale);
        userRepository.findByEmailIgnoreCase(emailNormalized)
                .filter(other -> !other.getId().equals(tenant.getId()))
                .ifPresent(other -> {
                    throw BusinessException.conflict(msg(locale, "tenants.detail.validation.emailInUseShort"));
                });

        String cleanPhone = clean(phone);
        if (cleanPhone != null) {
            userRepository.findByPhone(cleanPhone)
                    .filter(other -> !other.getId().equals(tenant.getId()))
                    .ifPresent(other -> {
                        throw BusinessException.conflict(msg(locale, "tenants.detail.validation.phoneInUse"));
                    });
        }

        boolean emailChanged = !Objects.equals(emailNormalized, tenant.getEmail());
        boolean phoneChanged = !Objects.equals(cleanPhone, tenant.getPhone());
        tenant.setFullName(cleanName);
        tenant.setEmail(emailNormalized);
        tenant.setPhone(cleanPhone);
        if (emailChanged) {
            tenant.setEmailVerified(true);
        }
        if (phoneChanged) {
            tenant.setPhoneVerified(false);
        }
    }

    @PostMapping("/{tenantId}/identity/{side}/delete")
    @Transactional
    public String deleteIdentityImage(@AuthenticationPrincipal AppUserDetails me,
                                      @PathVariable UUID tenantId,
                                      @PathVariable String side,
                                      Locale locale,
                                      RedirectAttributes ra) {
        try {
            User tenant = loadTenant(tenantId, locale);

            String label;
            if ("front".equalsIgnoreCase(side)) {
                localUploadService.deletePublicUrl(tenant.getIdentityFrontUrl());
                tenant.setIdentityFrontUrl(null);
                label = msg(locale, "tenants.detail.frontSide").toLowerCase(locale);
            } else if ("back".equalsIgnoreCase(side)) {
                localUploadService.deletePublicUrl(tenant.getIdentityBackUrl());
                tenant.setIdentityBackUrl(null);
                label = msg(locale, "tenants.detail.backSide").toLowerCase(locale);
            } else {
                throw BusinessException.badRequest(msg(locale, "tenants.detail.validation.invalidDocumentSide"));
            }

            tenant.setIdentityVerified(false);
            tenant.setIdentityVerifiedAt(null);
            tenant.setIdentityVerifiedBy(null);
            tenant.setIdentityUpdatedAt(OffsetDateTime.now());
            userRepository.save(tenant);

            notificationService.create(tenant.getId(), "PROFILE_DOCUMENT_DELETED",
                    "Ảnh giấy tờ đã được cập nhật",
                    "Admin đã xóa ảnh CCCD/CMND " + label + ". Vui lòng bổ sung lại khi cần.",
                    "/me/profile?tab=docs");
            FlashAlert.ok(ra, msg(locale, "tenants.detail.flash.documentDeleted", label));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/tenants/" + tenantId + "#identity";
    }

    @PostMapping("/{tenantId}/room")
    public String assignRoom(@AuthenticationPrincipal AppUserDetails me,
                             @PathVariable UUID tenantId,
                             @RequestParam(required = false) String roomId,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startedOn,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidUntil,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size,
                             @RequestParam(required = false) String returnTo,
                             Locale locale,
                             RedirectAttributes ra) {
        try {
            UUID selectedRoomId = null;
            if (roomId != null && !roomId.isBlank()) {
                selectedRoomId = UUID.fromString(roomId);
            }

            Room room = roomAdminService.assignTenantToRoom(me.getId(), tenantId, selectedRoomId, startedOn, paidUntil);
            if (room == null) {
                FlashAlert.ok(ra, msg(locale, "tenants.detail.flash.unassigned"));
            } else {
                FlashAlert.ok(ra, msg(locale, "tenants.detail.flash.assignedRoom", room.getCode() + " · " + room.getTitle()));
            }
        } catch (IllegalArgumentException ex) {
            FlashAlert.err(ra, msg(locale, "tenants.detail.validation.invalidRoomId"));
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }

        if ("detail".equals(returnTo)) {
            return "redirect:/admin/tenants/" + tenantId;
        }
        if (q != null && !q.isBlank()) ra.addAttribute("q", q);
        if (page != null && page > 0) ra.addAttribute("page", page);
        if (size != null && size > 0) ra.addAttribute("size", size);
        return "redirect:/admin/tenants";
    }

    private User loadTenant(UUID tenantId, Locale locale) {
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound(msg(locale, "tenants.tenant")));
        if (!tenant.hasRole(User.Role.TENANT)) {
            throw BusinessException.badRequest(msg(locale, "tenants.detail.validation.notTenantAccount"));
        }
        return tenant;
    }

    private String normalizeEmail(String value, Locale locale) {
        String clean = clean(value);
        if (clean == null) {
            throw BusinessException.badRequest(msg(locale, "tenants.validation.emailRequired"));
        }
        return clean.toLowerCase(java.util.Locale.ROOT);
    }

    private void validateNewPassword(String password, String confirmPassword, Locale locale) {
        String cleanPassword = clean(password);
        if (cleanPassword == null) {
            throw BusinessException.badRequest(msg(locale, "tenants.validation.passwordRequired"));
        }
        if (cleanPassword.length() < 8 || cleanPassword.length() > 64) {
            throw BusinessException.badRequest(msg(locale, "tenants.validation.passwordLength"));
        }
        String cleanConfirm = clean(confirmPassword);
        if (confirmPassword != null && !cleanPassword.equals(cleanConfirm)) {
            throw BusinessException.badRequest(msg(locale, "tenants.detail.validation.passwordMismatch"));
        }
    }

    private User.IdentityType parseIdentityType(String value, Locale locale) {
        String clean = clean(value);
        if (clean == null) return null;
        try {
            return User.IdentityType.valueOf(clean.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(msg(locale, "tenants.detail.validation.invalidDocumentType"));
        }
    }

    private static String clean(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isBlank() ? null : clean;
    }

    private String msg(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale);
    }
}
