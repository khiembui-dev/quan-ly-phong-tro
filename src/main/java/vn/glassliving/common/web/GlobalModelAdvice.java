package vn.glassliving.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.maintenance.entity.MaintenanceTicket;
import vn.glassliving.maintenance.repository.MaintenanceTicketRepository;
import vn.glassliving.notification.repository.NotificationRepository;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

@ControllerAdvice(basePackages = "vn.glassliving")
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationRepository notificationRepository;
    private final MaintenanceTicketRepository maintenanceTicketRepository;
    private final TemplateHelper templateHelper;

    @ModelAttribute("unreadNotifications")
    public Long unreadNotifications(@AuthenticationPrincipal AppUserDetails me) {
        if (me == null) return 0L;
        return notificationRepository.countByUserIdAndReadAtIsNull(me.getId());
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
                    .collect(Collectors.joining("&"));
        }
        String separator = filtered.isBlank() ? "?" : "?" + filtered + "&";
        return req.getRequestURI() + separator + "lang=" + lang;
    }
}
