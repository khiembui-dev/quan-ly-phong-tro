package vn.glassliving.admin.page.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.glassliving.ai.repository.AiQueryRepository;
import vn.glassliving.auth.security.AppUserDetails;

@Controller
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
public class AiPageController {

    private final AiQueryRepository aiQueryRepository;

    @GetMapping
    public String ai(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("activeNav", "ai");
        model.addAttribute("pageTitle", "Trợ lý AI");
        model.addAttribute("history", aiQueryRepository.findTop10ByUserIdOrderByCreatedAtDesc(me.getId()));
        return "admin/ai";
    }
}
