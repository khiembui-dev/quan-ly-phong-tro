package vn.glassliving.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.notification.service.NotificationService;

@Controller
@RequiredArgsConstructor
public class NotificationWebController {

    private final NotificationService notificationService;

    /** Returns an HTML fragment used by the navbar dropdown (HTMX). */
    @GetMapping("/notifications/dropdown")
    public String dropdown(@AuthenticationPrincipal AppUserDetails me, Model model) {
        if (me == null) return "fragments/notification-list :: empty";
        model.addAttribute("notifications", notificationService.latest(me.getId()));
        return "fragments/notification-list :: list";
    }

    @GetMapping({"/notifications", "/me/notifications"})
    public String page(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("notifications", notificationService.latest(me.getId()));
        return "customer/notifications";
    }
}
