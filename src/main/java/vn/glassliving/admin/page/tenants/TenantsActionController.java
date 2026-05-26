package vn.glassliving.admin.page.tenants;

import lombok.RequiredArgsConstructor;
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
                         RedirectAttributes ra) {
        try {
            String emailNormalized = normalizeEmail(email);
            String cleanName = clean(fullName);
            String cleanPhone = clean(phone);
            validateNewPassword(password, null);
            if (cleanName == null) {
                throw BusinessException.badRequest("Họ tên không được để trống.");
            }
            if (cleanPhone == null) {
                throw BusinessException.badRequest("Số điện thoại không được để trống.");
            }
            if (userRepository.existsByEmailIgnoreCase(emailNormalized)) {
                throw BusinessException.conflict("Email này đã được sử dụng. Khách có thể login trực tiếp.");
            }
            if (userRepository.existsByPhone(cleanPhone)) {
                throw BusinessException.conflict("Số điện thoại này đã được sử dụng.");
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

            FlashAlert.ok(ra, "Đã tạo tài khoản khách \"" + u.getFullName() + "\". Khách có thể đăng nhập ngay.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/tenants";
    }

    @PostMapping("/{tenantId}/password")
    @Transactional
    public String changePassword(@PathVariable UUID tenantId,
                                 @RequestParam String newPassword,
                                 @RequestParam(required = false) String confirmPassword,
                                 RedirectAttributes ra) {
        try {
            User tenant = loadTenant(tenantId);
            validateNewPassword(newPassword, confirmPassword);
            tenant.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
            userRepository.save(tenant);
            notificationService.create(tenant.getId(), "PASSWORD_CHANGED",
                    "Mật khẩu tài khoản đã được cập nhật",
                    "Admin vừa đổi mật khẩu đăng nhập cho tài khoản của bạn.",
                    "/me/profile");
            FlashAlert.ok(ra, "Đã đổi mật khẩu cho \"" + tenant.getFullName() + "\".");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/tenants/" + tenantId + "#security";
    }

    @PostMapping("/{tenantId}/identity")
    @Transactional
    public String updateIdentity(@AuthenticationPrincipal AppUserDetails me,
                                 @PathVariable UUID tenantId,
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
                                 RedirectAttributes ra) {
        try {
            User tenant = loadTenant(tenantId);

            tenant.setIdentityType(parseIdentityType(identityType));
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
            FlashAlert.ok(ra, identityVerified ? "Đã lưu và xác minh hồ sơ khách thuê." : "Đã lưu hồ sơ khách thuê.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/admin/tenants/" + tenantId + "#identity";
    }

    @PostMapping("/{tenantId}/identity/{side}/delete")
    @Transactional
    public String deleteIdentityImage(@AuthenticationPrincipal AppUserDetails me,
                                      @PathVariable UUID tenantId,
                                      @PathVariable String side,
                                      RedirectAttributes ra) {
        try {
            User tenant = loadTenant(tenantId);

            String label;
            if ("front".equalsIgnoreCase(side)) {
                localUploadService.deletePublicUrl(tenant.getIdentityFrontUrl());
                tenant.setIdentityFrontUrl(null);
                label = "mặt trước";
            } else if ("back".equalsIgnoreCase(side)) {
                localUploadService.deletePublicUrl(tenant.getIdentityBackUrl());
                tenant.setIdentityBackUrl(null);
                label = "mặt sau";
            } else {
                throw BusinessException.badRequest("Mặt ảnh CCCD không hợp lệ.");
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
            FlashAlert.ok(ra, "Đã xóa ảnh CCCD " + label + ".");
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
                             @RequestParam(required = false) String returnTo,
                             RedirectAttributes ra) {
        try {
            UUID selectedRoomId = null;
            if (roomId != null && !roomId.isBlank()) {
                selectedRoomId = UUID.fromString(roomId);
            }

            Room room = roomAdminService.assignTenantToRoom(me.getId(), tenantId, selectedRoomId, startedOn, paidUntil);
            if (room == null) {
                FlashAlert.ok(ra, "Đã đặt khách thuê về trạng thái đang trống.");
            } else {
                FlashAlert.ok(ra, "Đã gán khách thuê vào phòng \"" + room.getCode() + " · " + room.getTitle() + "\".");
            }
        } catch (IllegalArgumentException ex) {
            FlashAlert.err(ra, "Mã phòng không hợp lệ.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }

        if ("detail".equals(returnTo)) {
            return "redirect:/admin/tenants/" + tenantId;
        }
        if (q != null && !q.isBlank()) ra.addAttribute("q", q);
        if (page != null && page > 0) ra.addAttribute("page", page);
        return "redirect:/admin/tenants";
    }

    private User loadTenant(UUID tenantId) {
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> BusinessException.notFound("Khách thuê"));
        if (!tenant.hasRole(User.Role.TENANT)) {
            throw BusinessException.badRequest("Tài khoản đã chọn không phải khách thuê.");
        }
        return tenant;
    }

    private static String normalizeEmail(String value) {
        String clean = clean(value);
        if (clean == null) {
            throw BusinessException.badRequest("Email không được để trống.");
        }
        return clean.toLowerCase(java.util.Locale.ROOT);
    }

    private static void validateNewPassword(String password, String confirmPassword) {
        String cleanPassword = clean(password);
        if (cleanPassword == null) {
            throw BusinessException.badRequest("Mật khẩu không được để trống.");
        }
        if (cleanPassword.length() < 8 || cleanPassword.length() > 64) {
            throw BusinessException.badRequest("Mật khẩu phải từ 8 đến 64 ký tự.");
        }
        String cleanConfirm = clean(confirmPassword);
        if (confirmPassword != null && !cleanPassword.equals(cleanConfirm)) {
            throw BusinessException.badRequest("Mật khẩu xác nhận không khớp.");
        }
    }

    private static User.IdentityType parseIdentityType(String value) {
        String clean = clean(value);
        if (clean == null) return null;
        try {
            return User.IdentityType.valueOf(clean.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest("Loại giấy tờ không hợp lệ.");
        }
    }

    private static String clean(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isBlank() ? null : clean;
    }
}
