package vn.glassliving.customer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.dto.ApiResponse;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.storage.LocalUploadService;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.customer.profile.CustomerProfileValidator;
import vn.glassliving.favorite.repository.FavoriteRepository;
import vn.glassliving.favorite.service.FavoriteService;
import vn.glassliving.invoice.dto.InvoiceLineItem;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.notification.service.NotificationService;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class CustomerWebController {

    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 20, 50, 100);

    private final InvoiceRepository invoiceRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final FavoriteService favoriteService;
    private final NotificationService notificationService;
    private final RoomRepository roomRepository;
    private final InvoiceService invoiceService;
    private final LocalUploadService localUploadService;
    private final CustomerProfileValidator customerProfileValidator;

    @GetMapping({"/me/profile", "/customer/profile"})
    public String profile(@AuthenticationPrincipal AppUserDetails me, Model model) {
        User user = userRepository.findById(me.getId())
                .orElseThrow(() -> BusinessException.notFound("Tai khoan"));
        model.addAttribute("user", user);
        model.addAttribute("identityComplete", identityComplete(user));
        return "customer/profile";
    }

    @PostMapping({"/me/profile", "/customer/profile"})
    @Transactional
    public String updateProfile(@AuthenticationPrincipal AppUserDetails me,
                                @RequestParam String fullName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dob,
                                @RequestParam(required = false) String gender,
                                @RequestParam(required = false) String permanentAddress,
                                @RequestParam(required = false) MultipartFile avatar,
                                RedirectAttributes ra) {
        try {
            User user = userRepository.findById(me.getId())
                    .orElseThrow(() -> BusinessException.notFound("Tai khoan"));
            CustomerProfileValidator.ValidProfile profile =
                    customerProfileValidator.validateProfile(fullName, phone, dob, gender, permanentAddress);
            String cleanPhone = profile.phone();
            if (cleanPhone != null && !Objects.equals(cleanPhone, user.getPhone())) {
                userRepository.findByPhone(cleanPhone)
                        .filter(other -> !other.getId().equals(user.getId()))
                        .ifPresent(other -> {
                            throw BusinessException.conflict("So dien thoai nay da duoc su dung.");
                        });
            }
            user.setFullName(profile.fullName());
            user.setPhone(cleanPhone);
            user.setDob(profile.dob());
            user.setGender(profile.gender());
            user.setPermanentAddress(profile.permanentAddress());
            String avatarUrl = localUploadService.storeImage(avatar, "tenant-avatars/" + user.getId(), "avatar");
            if (avatarUrl != null) {
                localUploadService.deletePublicUrl(user.getAvatarUrl());
                user.setAvatarUrl(avatarUrl);
            }
            userRepository.save(user);
            FlashAlert.ok(ra, "Da cap nhat ho so ca nhan.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/me/profile";
    }

    @PostMapping({"/me/profile/identity", "/customer/profile/identity"})
    @Transactional
    public String updateIdentity(@AuthenticationPrincipal AppUserDetails me,
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
                                 RedirectAttributes ra) {
        try {
            User user = userRepository.findById(me.getId())
                    .orElseThrow(() -> BusinessException.notFound("Tai khoan"));
            CustomerProfileValidator.ValidIdentity identity =
                    customerProfileValidator.validateIdentity(identityType, identityNumber, identityIssuedDate,
                            identityIssuedPlace, permanentAddress);

            String folder = "tenant-docs/" + user.getId();
            String frontUrl = localUploadService.storeImage(identityFront, folder, "cccd-front");
            String backUrl = localUploadService.storeImage(identityBack, folder, "cccd-back");
            boolean hasFront = frontUrl != null || (!clearIdentityFront && clean(user.getIdentityFrontUrl()) != null);
            boolean hasBack = backUrl != null || (!clearIdentityBack && clean(user.getIdentityBackUrl()) != null);
            if (!hasFront) {
                throw BusinessException.badRequest("Vui long tai anh mat truoc giay to.");
            }
            if (!hasBack) {
                throw BusinessException.badRequest("Vui long tai anh mat sau giay to.");
            }

            user.setIdentityType(identity.identityType());
            user.setIdentityNumber(identity.identityNumber());
            user.setIdentityIssuedDate(identity.identityIssuedDate());
            user.setIdentityIssuedPlace(identity.identityIssuedPlace());
            user.setPermanentAddress(identity.permanentAddress());

            String oldFrontUrl = user.getIdentityFrontUrl();
            String oldBackUrl = user.getIdentityBackUrl();
            if (frontUrl != null) user.setIdentityFrontUrl(frontUrl);
            if (backUrl != null) user.setIdentityBackUrl(backUrl);
            if (clearIdentityFront && frontUrl == null) user.setIdentityFrontUrl(null);
            if (clearIdentityBack && backUrl == null) user.setIdentityBackUrl(null);
            if (frontUrl != null) localUploadService.deletePublicUrl(oldFrontUrl);
            if (backUrl != null) localUploadService.deletePublicUrl(oldBackUrl);

            user.setIdentityVerified(false);
            user.setIdentityVerifiedAt(null);
            user.setIdentityVerifiedBy(null);
            user.setIdentityUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);
            notificationService.create(user.getId(), "PROFILE_UPDATED",
                    "Ho so dinh danh da duoc cap nhat",
                    "Chu tro co the kiem tra va xac minh CCCD/CMND cua ban.",
                    "/me/profile?tab=docs");
            FlashAlert.ok(ra, "Da luu ho so giay to.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/me/profile?tab=docs";
    }

    @GetMapping("/me/contracts")
    public String contracts() {
        return "redirect:/me/invoices";
    }

    @GetMapping({"/me/invoices", "/customer/invoices"})
    public String invoices(@AuthenticationPrincipal AppUserDetails me,
                           @RequestParam(required = false) Integer month,
                           @RequestParam(required = false) Integer year,
                           @RequestParam(required = false) String status,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "10") int size,
                           Model model) {
        Invoice.InvoiceStatus statusFilter = parseInvoiceStatus(status);
        Short monthFilter = normalizeMonth(month);
        Short yearFilter = normalizeYear(year);
        int sizeSafe = normalizePageSize(size);
        Page<Invoice> invoicePage = invoiceRepository.searchByTenantUserId(
                me.getId(),
                statusFilter != null ? statusFilter.name() : "",
                monthFilter != null ? monthFilter : (short) 0,
                yearFilter != null ? yearFilter : (short) 0,
                PageRequest.of(Math.max(0, page), sizeSafe));
        List<Invoice> invoices = invoicePage.getContent();
        List<Invoice> allInvoices = invoiceRepository.findByTenantUserIdOrderByIssueDateDescCreatedAtDesc(me.getId());
        Map<UUID, List<InvoiceLineItem>> invoiceLineMap = new HashMap<>();
        for (Invoice invoice : invoices) {
            invoiceLineMap.put(invoice.getId(), invoiceService.parseOtherItems(invoice));
        }
        long paidCount = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.PAID).count();
        long openCount = allInvoices.stream()
                .filter(i -> i.getStatus() == Invoice.InvoiceStatus.PENDING
                        || i.getStatus() == Invoice.InvoiceStatus.PARTIALLY_PAID)
                .count();
        long overdueCount = allInvoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.OVERDUE).count();
        BigDecimal totalDebt = allInvoices.stream()
                .filter(CustomerWebController::isOpenInvoice)
                .map(CustomerWebController::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidTotal = allInvoices.stream()
                .map(Invoice::getPaidAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdueTotal = allInvoices.stream()
                .filter(i -> i.getStatus() == Invoice.InvoiceStatus.OVERDUE)
                .map(CustomerWebController::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Short> invoiceYears = allInvoices.stream()
                .map(Invoice::getPeriodYear)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
        model.addAttribute("invoices", invoices);
        model.addAttribute("invoicePage", invoicePage);
        model.addAttribute("invoiceLineMap", invoiceLineMap);
        model.addAttribute("paidCount", paidCount);
        model.addAttribute("openCount", openCount);
        model.addAttribute("overdueCount", overdueCount);
        model.addAttribute("totalDebt", totalDebt);
        model.addAttribute("paidTotal", paidTotal);
        model.addAttribute("overdueTotal", overdueTotal);
        model.addAttribute("invoiceYears", invoiceYears);
        model.addAttribute("filterMonth", monthFilter);
        model.addAttribute("filterYear", yearFilter);
        model.addAttribute("filterStatus", statusFilter != null ? statusFilter.name() : "");
        model.addAttribute("pageSize", sizeSafe);
        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        return "customer/invoices";
    }

    @GetMapping("/me/favorites")
    public String favorites(@AuthenticationPrincipal AppUserDetails me, Model model) {
        var favs = favoriteRepository.findByUserIdOrderByCreatedAtDesc(me.getId());
        List<Room> rooms = favs.stream()
                .map(f -> roomRepository.findById(f.getRoomId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        model.addAttribute("rooms", rooms);
        return "customer/favorites";
    }

    @PostMapping({"/me/favorites/{roomId}", "/customer/favorites/{roomId}"})
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> toggleFavorite(
            @AuthenticationPrincipal AppUserDetails me,
            @PathVariable UUID roomId) {
        boolean favorite = favoriteService.toggle(me.getId(), roomId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("favorite", favorite)));
    }

    private static int normalizePageSize(int size) {
        if (size <= 10) return 10;
        if (size <= 20) return 20;
        if (size <= 50) return 50;
        return 100;
    }

    private static Short normalizeMonth(Integer month) {
        if (month == null || month < 1 || month > 12) return null;
        return month.shortValue();
    }

    private static Short normalizeYear(Integer year) {
        if (year == null || year < 2000 || year > 2100) return null;
        return year.shortValue();
    }

    private static boolean isOpenInvoice(Invoice invoice) {
        return invoice != null && (invoice.getStatus() == Invoice.InvoiceStatus.PENDING
                || invoice.getStatus() == Invoice.InvoiceStatus.PARTIALLY_PAID
                || invoice.getStatus() == Invoice.InvoiceStatus.OVERDUE);
    }

    private static BigDecimal remainingAmount(Invoice invoice) {
        if (invoice == null) return BigDecimal.ZERO;
        BigDecimal total = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal remaining = total.subtract(paid);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    private static Invoice.InvoiceStatus parseInvoiceStatus(String value) {
        String clean = clean(value);
        if (clean == null) return null;
        try {
            return Invoice.InvoiceStatus.valueOf(clean.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean identityComplete(User user) {
        return user.getIdentityType() != null
                && clean(user.getIdentityNumber()) != null
                && clean(user.getIdentityFrontUrl()) != null
                && clean(user.getIdentityBackUrl()) != null;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isBlank() ? null : clean;
    }
}
