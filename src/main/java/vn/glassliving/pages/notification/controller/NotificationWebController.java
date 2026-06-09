package vn.glassliving.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.repository.UserRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.entity.MaintenanceTicketMessage;
import vn.glassliving.maintenance.repository.MaintenanceTicketMessageRepository;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.notification.service.NotificationService;
import vn.glassliving.room.entity.Room;
import vn.glassliving.room.repository.RoomRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class NotificationWebController {

    private final NotificationService notificationService;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final MaintenanceTicketMessageRepository ticketMessageRepository;
    private final RoomRepository roomRepository;
    private final AutomationSettingRepository automationSettingRepository;
    private final UserRepository userRepository;

    @Value("${app.customer-support.phone:}")
    private String supportPhone;

    @Value("${app.customer-support.email:hotro@smartrent.vn}")
    private String supportEmail;

    @Value("${app.customer-support.zalo:}")
    private String supportZalo;

    @GetMapping("/notifications/dropdown")
    public String dropdown(@AuthenticationPrincipal AppUserDetails me, Model model) {
        if (me == null) return "fragments/notification-list :: empty";
        model.addAttribute("notifications", notificationService.latest(me.getId()));
        return "fragments/notification-list :: list";
    }

    @GetMapping({"/notifications", "/me/notifications", "/customer/notifications"})
    public String page(@AuthenticationPrincipal AppUserDetails me, Model model) {
        var tickets = maintenanceTicketRepository.findTop8ByReporterUserIdOrderByReportedAtDesc(me.getId());
        var currentRooms = roomRepository.findByCurrentTenantId(me.getId());
        Map<UUID, String> roomLabels = new HashMap<>();
        currentRooms.forEach(room -> roomLabels.put(room.getId(), room.getCode() + " · " + room.getTitle()));
        List<UUID> ticketIds = tickets.stream().map(MaintenanceTicket::getId).toList();
        Map<UUID, List<MaintenanceTicketMessage>> messageMap = ticketIds.isEmpty()
                ? Map.of()
                : ticketMessageRepository.findByTicketIdInOrderByCreatedAtAsc(ticketIds).stream()
                        .collect(Collectors.groupingBy(MaintenanceTicketMessage::getTicketId));
        Map<UUID, List<String>> ticketAttachmentMap = new HashMap<>();
        Map<UUID, List<String>> messageAttachmentMap = new HashMap<>();
        for (MaintenanceTicket ticket : tickets) {
            ticketAttachmentMap.put(ticket.getId(), splitPhotos(ticket.getPhotoUrls()));
        }
        messageMap.values().stream()
                .flatMap(List::stream)
                .forEach(message -> messageAttachmentMap.put(message.getId(), splitPhotos(message.getPhotoUrls())));
        long openTicketCount = maintenanceTicketRepository.countByReporterUserIdAndStatusIn(me.getId(), List.of(
                MaintenanceTicket.Status.OPEN,
                MaintenanceTicket.Status.ACKNOWLEDGED,
                MaintenanceTicket.Status.IN_PROGRESS,
                MaintenanceTicket.Status.AWAITING_PARTS));
        long totalTicketCount = maintenanceTicketRepository.countByReporterUserId(me.getId());
        model.addAttribute("tickets", tickets);
        model.addAttribute("currentRooms", currentRooms);
        model.addAttribute("roomLabels", roomLabels);
        model.addAttribute("contactCards", contactCards(currentRooms));
        model.addAttribute("messageMap", messageMap);
        model.addAttribute("ticketAttachmentMap", ticketAttachmentMap);
        model.addAttribute("messageAttachmentMap", messageAttachmentMap);
        model.addAttribute("openTicketCount", openTicketCount);
        model.addAttribute("totalTicketCount", totalTicketCount);
        model.addAttribute("closedTicketCount", Math.max(0, totalTicketCount - openTicketCount));
        model.addAttribute("statusLabels", statusLabels());
        model.addAttribute("priorityLabels", priorityLabels());
        model.addAttribute("categoryLabels", categoryLabels());
        return "customer/notifications";
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
        if (ownerIds.isEmpty()) {
            String email = firstNonBlank(fallback != null ? fallback.getContactEmail() : null, supportEmail);
            String zalo = firstNonBlank(fallback != null ? fallback.getContactZalo() : null, supportZalo, supportPhone);
            cards.add(contactCard("Bộ phận hỗ trợ", email, firstNonBlank(supportPhone, zalo), zalo));
            return cards;
        }
        for (UUID ownerId : ownerIds) {
            User owner = userRepository.findById(ownerId).orElse(null);
            AutomationSetting setting = automationSettingRepository.findByOwnerId(ownerId).orElse(null);
            String email = firstNonBlank(
                    setting != null ? setting.getContactEmail() : null,
                    fallback != null ? fallback.getContactEmail() : null,
                    supportEmail);
            String zalo = firstNonBlank(
                    setting != null ? setting.getContactZalo() : null,
                    fallback != null ? fallback.getContactZalo() : null,
                    supportZalo,
                    owner != null ? owner.getPhone() : null);
            String phone = firstNonBlank(owner != null ? owner.getPhone() : null, supportPhone, zalo);
            cards.add(contactCard(owner != null ? owner.getFullName() : "Chủ trọ", email, phone, zalo));
        }
        return cards;
    }

    private static ContactCard contactCard(String ownerName, String email, String phone, String zalo) {
        return new ContactCard(ownerName, email, phone, phoneUrl(phone), zalo, zaloUrl(zalo));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static List<String> splitPhotos(String photoUrls) {
        if (photoUrls == null || photoUrls.isBlank()) return List.of();
        return java.util.Arrays.stream(photoUrls.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private static Map<String, String> statusLabels() {
        return Map.of(
                "OPEN", "Mới",
                "ACKNOWLEDGED", "Đã tiếp nhận",
                "IN_PROGRESS", "Đang xử lý",
                "AWAITING_PARTS", "Chờ vật tư",
                "RESOLVED", "Đã hoàn thành",
                "CLOSED", "Đã đóng",
                "CANCELLED", "Đã hủy");
    }

    private static Map<String, String> priorityLabels() {
        return Map.of(
                "LOW", "Thấp",
                "NORMAL", "Bình thường",
                "HIGH", "Cần xử lý sớm",
                "URGENT", "Khẩn cấp");
    }

    private static Map<String, String> categoryLabels() {
        return Map.of(
                "ELECTRICAL", "Điện",
                "PLUMBING", "Nước",
                "AC_HVAC", "Máy lạnh",
                "FURNITURE", "Nội thất",
                "APPLIANCE", "Thiết bị",
                "INTERNET", "Internet",
                "CLEANING", "Vệ sinh",
                "SECURITY", "An ninh",
                "OTHER", "Khác");
    }

    private static String phoneUrl(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String normalized = phone.replaceAll("[^0-9+]", "");
        return normalized.isBlank() ? null : "tel:" + normalized;
    }

    private static String zaloUrl(String zalo) {
        if (zalo == null || zalo.isBlank()) return null;
        String digits = zalo.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : "https://zalo.me/" + digits;
    }

    public record ContactCard(String ownerName, String email, String phone, String phoneUrl, String zalo, String zaloUrl) {}
}
