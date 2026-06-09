package vn.glassliving.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.glassliving.ai.dto.AiChatRequest;
import vn.glassliving.ai.dto.AiChatResponse;
import vn.glassliving.ai.entity.AiConversation;
import vn.glassliving.ai.service.AiChatService;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.exception.BusinessException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request,
                               @AuthenticationPrincipal AppUserDetails me) {
        AppUserDetails principal = requirePrincipal(me);
        return aiChatService.chat(principal.getId(), roleOf(principal), request);
    }

    @GetMapping("/conversations")
    public List<AiChatService.ConversationSummary> conversations(@AuthenticationPrincipal AppUserDetails me) {
        AppUserDetails principal = requirePrincipal(me);
        return aiChatService.listConversations(principal.getId(), roleOf(principal));
    }

    @GetMapping("/conversations/{conversationId}")
    public AiChatService.ConversationDetail conversation(@AuthenticationPrincipal AppUserDetails me,
                                                         @PathVariable UUID conversationId) {
        AppUserDetails principal = requirePrincipal(me);
        return aiChatService.getConversation(principal.getId(), roleOf(principal), conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public AiChatResponse deleteConversation(@AuthenticationPrincipal AppUserDetails me,
                                             @PathVariable UUID conversationId) {
        AppUserDetails principal = requirePrincipal(me);
        aiChatService.deleteConversation(principal.getId(), roleOf(principal), conversationId);
        return AiChatResponse.ok(null, "Đã xóa cuộc trò chuyện.", List.of());
    }

    private AppUserDetails requirePrincipal(AppUserDetails me) {
        if (me == null) throw BusinessException.unauthorized("Yêu cầu đăng nhập.");
        return me;
    }

    private AiConversation.Role roleOf(AppUserDetails me) {
        boolean admin = me.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_OWNER") || role.equals("ROLE_STAFF"));
        return admin ? AiConversation.Role.ADMIN : AiConversation.Role.CUSTOMER;
    }
}
