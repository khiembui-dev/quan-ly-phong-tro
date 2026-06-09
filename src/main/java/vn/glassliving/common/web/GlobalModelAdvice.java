package vn.glassliving.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.glassliving.automation.entity.AutomationSetting;
import vn.glassliving.automation.repository.AutomationSettingRepository;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.favorite.repository.FavoriteRepository;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.notification.repository.NotificationRepository;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ControllerAdvice(basePackages = "vn.glassliving")
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationRepository notificationRepository;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final AutomationSettingRepository automationSettingRepository;
    private final FavoriteRepository favoriteRepository;
    private final TemplateHelper templateHelper;

    @Value("${app.customer-support.phone:}")
    private String supportPhone;

    @Value("${app.customer-support.email:hotro@smartrent.vn}")
    private String supportEmail;

    @Value("${app.customer-support.zalo:}")
    private String supportZalo;

    @ModelAttribute("unreadNotifications")
    public Long unreadNotifications(@AuthenticationPrincipal AppUserDetails me) {
        if (me == null) return 0L;
        return notificationRepository.countByUserIdAndReadAtIsNull(me.getId());
    }

    @ModelAttribute("customerSupportBadge")
    public Long customerSupportBadge(@AuthenticationPrincipal AppUserDetails me, HttpServletRequest req) {
        if (me == null) return 0L;
        if (req != null && req.getRequestURI().startsWith("/admin")) return 0L;
        return maintenanceTicketRepository.countByReporterUserIdAndStatusIn(me.getId(), java.util.List.of(
                MaintenanceTicket.Status.OPEN,
                MaintenanceTicket.Status.ACKNOWLEDGED,
                MaintenanceTicket.Status.IN_PROGRESS,
                MaintenanceTicket.Status.AWAITING_PARTS
        ));
    }

    @ModelAttribute("favoriteRoomIds")
    public Set<java.util.UUID> favoriteRoomIds(@AuthenticationPrincipal AppUserDetails me, HttpServletRequest req) {
        if (me == null) return Set.of();
        if (req != null && req.getRequestURI().startsWith("/admin")) return Set.of();
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(favorite -> favorite.getRoomId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Open maintenance tickets for the current owner — used by the admin sidebar badge.
     * Returns 0 for non-owners or unauthenticated; only counted on /admin/* requests.
     */
    @ModelAttribute("openTickets")
    public Long openTickets(@AuthenticationPrincipal AppUserDetails me, HttpServletRequest req) {
        if (me == null) return 0L;
        if (req != null && !req.getRequestURI().startsWith("/admin")) return 0L;
        try {
            return maintenanceTicketRepository.countByOwnerIdAndStatus(me.getId(), MaintenanceTicket.Status.OPEN);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** Exposes static utility methods to all Thymeleaf templates as {@code ${h}}. */
    @ModelAttribute("h")
    public TemplateHelper helpers() {
        return templateHelper;
    }

    @ModelAttribute
    public void exposeCustomerSupport(Model model) {
        AutomationSetting setting = automationSettingRepository
                .findFirstByContactEmailIsNotNullOrContactZaloIsNotNullOrderByUpdatedAtDesc()
                .orElse(null);
        String phone = firstNonBlank(setting != null ? setting.getContactZalo() : null, supportZalo, supportPhone);
        String zalo = firstNonBlank(supportZalo, phone);
        if (setting != null && firstNonBlank(setting.getContactZalo()) != null) {
            zalo = setting.getContactZalo();
        }
        model.addAttribute("supportPhone", phone);
        model.addAttribute("supportPhoneUrl", telUrl(phone));
        model.addAttribute("supportEmail", firstNonBlank(setting != null ? setting.getContactEmail() : null, supportEmail, "hotro@smartrent.vn"));
        model.addAttribute("supportZaloUrl", zaloUrl(zalo));
    }

    @ModelAttribute
    public void exposeLocale(HttpServletRequest req, Locale locale, Model model) {
        String lang = "en".equalsIgnoreCase(locale.getLanguage()) ? "en" : "vi";
        model.addAttribute("currentLang", lang);
        model.addAttribute("langSwitchViUrl", languageSwitchUrl(req, "vi"));
        model.addAttribute("langSwitchEnUrl", languageSwitchUrl(req, "en"));
    }

    /**
     * Bridge session-scoped flash messages (set by GlobalExceptionHandler) into the model
     * so the {@code fragments/flash :: alert} fragment can render them. Cleared after read
     * to behave like one-shot flash attributes.
     */
    @ModelAttribute
    public void exposeSessionFlash(HttpServletRequest req, Model model) {
        HttpSession session = req.getSession(false);
        if (session == null) return;
        Object kind = session.getAttribute("flashKind");
        Object message = session.getAttribute("flashMessage");
        if (message != null && !model.containsAttribute("flashMessage")) {
            model.addAttribute("flashKind", kind);
            model.addAttribute("flashMessage", message);
            session.removeAttribute("flashKind");
            session.removeAttribute("flashMessage");
        }
    }

    private static String languageSwitchUrl(HttpServletRequest req, String lang) {
        String query = req.getQueryString();
        String filtered = "";
        if (query != null && !query.isBlank()) {
            filtered = Arrays.stream(query.split("&"))
                    .filter(part -> !part.isBlank())
                    .filter(part -> !part.startsWith("lang="))
                    .filter(part -> !isBadGeneratedFilterParam(part))
                    .collect(Collectors.joining("&"));
        }
        String separator = filtered.isBlank() ? "?" : "?" + filtered + "&";
        return req.getRequestURI() + separator + "lang=" + lang;
    }

    private static boolean isBadGeneratedFilterParam(String part) {
        String normalized = part.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("q=filterq")
                || normalized.equals("q=q")
                || normalized.equals("state=filterstate")
                || normalized.equals("status=filterstatus");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String telUrl(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[^0-9+]", "");
        return normalized.isBlank() ? null : "tel:" + normalized;
    }

    private static String zaloUrl(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : "https://zalo.me/" + digits;
    }
}
