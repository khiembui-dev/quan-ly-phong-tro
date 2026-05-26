package vn.glassliving.customer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;
import vn.glassliving.common.storage.LocalUploadService;
import vn.glassliving.common.web.FlashAlert;
import vn.glassliving.favorite.repository.FavoriteRepository;
import vn.glassliving.favorite.service.FavoriteService;
import vn.glassliving.invoice.dto.InvoiceLineItem;
import vn.glassliving.invoice.entity.Invoice;
import vn.glassliving.invoice.repository.InvoiceRepository;
import vn.glassliving.invoice.service.InvoiceService;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.maintenance.service.MaintenanceService;
import vn.glassliving.notification.service.NotificationService;
import vn.glassliving.property.repository.PropertyRepository;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;
import vn.glassliving.room.service.RoomService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class CustomerWebController {

    private final InvoiceRepository invoiceRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final FavoriteService favoriteService;
    private final NotificationService notificationService;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final InvoiceService invoiceService;
    private final LocalUploadService localUploadService;
    private final MaintenanceTicketRepository ticketRepository;
    private final MaintenanceService maintenanceService;
    private final AutomationSettingRepository automationSettingRepository;
    private final PropertyRepository propertyRepository;

    @GetMapping("/me")
    public String dashboard(@AuthenticationPrincipal AppUserDetails me, Model model) {
        UUID userId = me.getId();
        var currentRooms = roomRepository.findByCurrentTenantId(userId);
        var invoices = invoiceRepository.findTop5ByTenantUserIdOrderByIssueDateDesc(userId);
        var tickets = ticketRepository.findTop8ByReporterUserIdOrderByReportedAtDesc(userId);

        BigDecimal monthlyDue = invoices.stream()
                .filter(CustomerWebController::isOpenInvoice)
                .map(Invoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        long unpaidCount = invoices.stream()
                .filter(CustomerWebController::isOpenInvoice)
                .count();
        long openTicketCount = ticketRepository.countByReporterUserIdAndStatusIn(userId, List.of(
                MaintenanceTicket.Status.OPEN,
                MaintenanceTicket.Status.ACKNOWLEDGED,
                MaintenanceTicket.Status.IN_PROGRESS,
                MaintenanceTicket.Status.AWAITING_PARTS
        ));

        Map<UUID, String> propertyLabels = new HashMap<>();
        for (Room room : currentRooms) {
            propertyRepository.findById(room.getPropertyId())
                    .ifPresent(property -> propertyLabels.put(property.getId(), property.getName()));
        }
        Map<UUID, String> roomLabels = new HashMap<>();
        for (Room room : currentRooms) {
            roomLabels.put(room.getId(), room.getCode() + " · " + room.getTitle());
        }

        model.addAttribute("activeRooms", currentRooms.size());
        model.addAttribute("currentRooms", currentRooms);
        model.addAttribute("propertyLabels", propertyLabels);
        model.addAttribute("roomLabels", roomLabels);
        model.addAttribute("contactCards", contactCards(currentRooms));
        model.addAttribute("monthlyDue", monthlyDue);
        model.addAttribute("unpaidCount", unpaidCount);
        model.addAttribute("favoriteCount", favoriteService.count(userId));
        model.addAttribute("recentInvoices", invoices);
        model.addAttribute("tickets", tickets);
        model.addAttribute("ticketCount", ticketRepository.countByReporterUserId(userId));
        model.addAttribute("openTicketCount", openTicketCount);
        model.addAttribute("notifications", notificationService.latest(userId));
        return "customer/dashboard";
    }

    @PostMapping("/me/tickets")
    public String createTicket(@AuthenticationPrincipal AppUserDetails me,
                               @RequestParam UUID roomId,
                               @RequestParam(defaultValue = "OTHER") String category,
                               @RequestParam(defaultValue = "NORMAL") String priority,
                               @RequestParam String title,
                               @RequestParam(required = false) String description,
                               RedirectAttributes ra) {
        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> BusinessException.notFound("Phòng"));
            if (!me.getId().equals(room.getCurrentTenantId())) {
                throw BusinessException.forbidden("Bạn chỉ có thể tạo ticket cho phòng đang ở.");
            }
            MaintenanceTicket ticket = maintenanceService.createForReporter(
                    me.getId(),
                    room.getOwnerId(),
                    room.getPropertyId(),
                    room.getId(),
                    category,
                    priority,
                    title,
                    clean(description));
            FlashAlert.ok(ra, "Đã gửi ticket " + ticket.getCode() + " cho chủ trọ.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            FlashAlert.err(ra, "Dữ liệu ticket không hợp lệ.");
        }
        return "redirect:/me#tickets";
    }

    @GetMapping("/me/profile")
    public String profile(@AuthenticationPrincipal AppUserDetails me, Model model) {
        User user = userRepository.findById(me.getId())
                .orElseThrow(() -> BusinessException.notFound("Tài khoản"));
        model.addAttribute("user", user);
        model.addAttribute("identityComplete", identityComplete(user));
        return "customer/profile";
    }

    @PostMapping("/me/profile")
    @Transactional
    public String updateProfile(@AuthenticationPrincipal AppUserDetails me,
                                @RequestParam String fullName,
                                @RequestParam(required = false) String phone,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dob,
                                @RequestParam(required = false) String gender,
                                @RequestParam(required = false) String permanentAddress,
                                RedirectAttributes ra) {
        try {
            User user = userRepository.findById(me.getId())
                    .orElseThrow(() -> BusinessException.notFound("Tài khoản"));
            String name = clean(fullName);
            if (name == null) {
                throw BusinessException.badRequest("Họ tên không được để trống.");
            }
            String cleanPhone = clean(phone);
            if (cleanPhone != null && !Objects.equals(cleanPhone, user.getPhone())) {
                userRepository.findByPhone(cleanPhone)
                        .filter(other -> !other.getId().equals(user.getId()))
                        .ifPresent(other -> {
                            throw BusinessException.conflict("Số điện thoại này đã được sử dụng.");
                        });
            }
            user.setFullName(name);
            user.setPhone(cleanPhone);
            user.setDob(dob);
            user.setGender(parseGender(gender));
            user.setPermanentAddress(clean(permanentAddress));
            userRepository.save(user);
            FlashAlert.ok(ra, "Đã cập nhật hồ sơ cá nhân.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/me/profile";
    }

    @PostMapping("/me/profile/identity")
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
                    .orElseThrow(() -> BusinessException.notFound("Tài khoản"));
            applyIdentityFields(user, identityType, identityNumber, identityIssuedDate, identityIssuedPlace, permanentAddress);

            String folder = "tenant-docs/" + user.getId();
            String frontUrl = localUploadService.storeImage(identityFront, folder, "cccd-front");
            String backUrl = localUploadService.storeImage(identityBack, folder, "cccd-back");
            if (frontUrl != null) user.setIdentityFrontUrl(frontUrl);
            if (backUrl != null) user.setIdentityBackUrl(backUrl);
            if (clearIdentityFront && frontUrl == null) user.setIdentityFrontUrl(null);
            if (clearIdentityBack && backUrl == null) user.setIdentityBackUrl(null);

            user.setIdentityVerified(false);
            user.setIdentityVerifiedAt(null);
            user.setIdentityVerifiedBy(null);
            user.setIdentityUpdatedAt(OffsetDateTime.now());
            userRepository.save(user);
            notificationService.create(user.getId(), "PROFILE_UPDATED",
                    "Hồ sơ định danh đã được cập nhật",
                    "Chủ trọ có thể kiểm tra và xác minh CCCD/CMND của bạn.",
                    "/me/profile?tab=docs");
            FlashAlert.ok(ra, "Đã lưu hồ sơ giấy tờ. Trạng thái sẽ chuyển sang chờ admin xác minh.");
        } catch (BusinessException ex) {
            FlashAlert.err(ra, ex.getMessage());
        }
        return "redirect:/me/profile?tab=docs";
    }

    @GetMapping("/me/contracts")
    public String contracts(@AuthenticationPrincipal AppUserDetails me, Model model) {
        return "redirect:/me/invoices";
    }

    @GetMapping("/me/invoices")
    public String invoices(@AuthenticationPrincipal AppUserDetails me, Model model) {
        var page = invoiceRepository.findByTenantUserId(me.getId(),
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "issueDate")));
        List<Invoice> invoices = page.getContent();
        Map<UUID, List<InvoiceLineItem>> invoiceLineMap = new HashMap<>();
        for (Invoice invoice : invoices) {
            invoiceLineMap.put(invoice.getId(), invoiceService.parseOtherItems(invoice));
        }
        long paidCount = invoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.PAID).count();
        long openCount = invoices.stream()
                .filter(i -> i.getStatus() == Invoice.InvoiceStatus.PENDING
                        || i.getStatus() == Invoice.InvoiceStatus.PARTIALLY_PAID)
                .count();
        long overdueCount = invoices.stream().filter(i -> i.getStatus() == Invoice.InvoiceStatus.OVERDUE).count();
        model.addAttribute("invoices", invoices);
        model.addAttribute("invoiceLineMap", invoiceLineMap);
        model.addAttribute("paidCount", paidCount);
        model.addAttribute("openCount", openCount);
        model.addAttribute("overdueCount", overdueCount);
        return "customer/invoices";
    }

    @GetMapping("/me/favorites")
    public String favorites(@AuthenticationPrincipal AppUserDetails me, Model model) {
        var favs = favoriteRepository.findByUserIdOrderByCreatedAtDesc(me.getId());
        List<Room> rooms = favs.stream()
                .map(f -> roomRepository.findById(f.getRoomId()).orElse(null))
                .filter(r -> r != null)
                .toList();
        model.addAttribute("rooms", rooms);
        return "customer/favorites";
    }

    @GetMapping("/booking/start")
    public String bookingStart(@RequestParam("roomId") UUID roomId, Model model) {
        Room room = roomService.getById(roomId);
        if (room.getStatus() != Room.RoomStatus.AVAILABLE) {
            return "redirect:/rooms/" + room.getSlug() + "?error=not-available";
        }
        model.addAttribute("room", room);
        return "customer/booking";
    }

    @GetMapping("/me/bookings/{id}/success")
    public String bookingSuccess(@PathVariable UUID id) {
        return "customer/booking-success";
    }

    private List<ContactCard> contactCards(List<Room> currentRooms) {
        Set<UUID> ownerIds = new LinkedHashSet<>();
        for (Room room : currentRooms) {
            if (room.getOwnerId() != null) ownerIds.add(room.getOwnerId());
        }
        AutomationSetting fallback = automationSettingRepository
                .findFirstByContactEmailIsNotNullOrContactZaloIsNotNullOrderByUpdatedAtDesc()
                .orElse(null);
        List<ContactCard> cards = new ArrayList<>();
        if (ownerIds.isEmpty() && fallback != null) {
            String email = clean(fallback.getContactEmail());
            String zalo = clean(fallback.getContactZalo());
            if (email != null || zalo != null) {
                cards.add(new ContactCard(null, "Bộ phận hỗ trợ", email, zalo, zaloUrl(zalo)));
            }
            return cards;
        }
        for (UUID ownerId : ownerIds) {
            User owner = userRepository.findById(ownerId).orElse(null);
            AutomationSetting setting = automationSettingRepository.findByOwnerId(ownerId).orElse(null);
            String email = setting != null ? clean(setting.getContactEmail()) : null;
            String zalo = setting != null ? clean(setting.getContactZalo()) : null;
            if (email == null && fallback != null) email = clean(fallback.getContactEmail());
            if (zalo == null && fallback != null) zalo = clean(fallback.getContactZalo());
            cards.add(new ContactCard(
                    ownerId,
                    owner != null ? owner.getFullName() : "Chủ trọ",
                    email,
                    zalo,
                    zaloUrl(zalo)
            ));
        }
        return cards;
    }

    private static boolean isOpenInvoice(Invoice invoice) {
        return invoice != null && (invoice.getStatus() == Invoice.InvoiceStatus.PENDING
                || invoice.getStatus() == Invoice.InvoiceStatus.PARTIALLY_PAID
                || invoice.getStatus() == Invoice.InvoiceStatus.OVERDUE);
    }

    private static String zaloUrl(String zalo) {
        String clean = clean(zalo);
        if (clean == null) return null;
        String digits = clean.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return null;
        if (digits.startsWith("0")) digits = "84" + digits.substring(1);
        return "https://zalo.me/" + digits;
    }

    public record ContactCard(UUID ownerId, String ownerName, String email, String zalo, String zaloUrl) {}

    private static void applyIdentityFields(User user,
                                            String identityType,
                                            String identityNumber,
                                            LocalDate identityIssuedDate,
                                            String identityIssuedPlace,
                                            String permanentAddress) {
        user.setIdentityType(parseIdentityType(identityType));
        user.setIdentityNumber(clean(identityNumber));
        user.setIdentityIssuedDate(identityIssuedDate);
        user.setIdentityIssuedPlace(clean(identityIssuedPlace));
        user.setPermanentAddress(clean(permanentAddress));
    }

    private static boolean identityComplete(User user) {
        return user.getIdentityType() != null
                && clean(user.getIdentityNumber()) != null
                && clean(user.getIdentityFrontUrl()) != null
                && clean(user.getIdentityBackUrl()) != null;
    }

    private static User.Gender parseGender(String value) {
        String clean = clean(value);
        if (clean == null) return null;
        try {
            return User.Gender.valueOf(clean.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
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
