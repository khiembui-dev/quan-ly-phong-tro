package vn.glassliving.ai.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.glassliving.ai.dto.AiChatRequest;
import vn.glassliving.ai.dto.AiChatResponse;
import vn.glassliving.ai.entity.AiConversation;
import vn.glassliving.ai.service.AiChatService;
import vn.glassliving.ai.service.AiAssistantService;
import vn.glassliving.auth.security.AppUserDetails;
import vn.glassliving.common.dto.ApiResponse;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiApiController {

    private final AiAssistantService aiService;
    private final AiChatService aiChatService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, String>>> chat(@RequestBody ChatRequest req,
                                                                  @AuthenticationPrincipal AppUserDetails me) {
        UUID userId = me != null ? me.getId() : UUID.fromString("00000000-0000-0000-0000-000000000000");
        if (me != null) {
            AiChatResponse response = aiChatService.chat(userId, roleOf(me),
                    new AiChatRequest(req.prompt(), null));
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "reply", response.message(),
                    "conversationId", response.conversationId() != null ? response.conversationId().toString() : "")));
        }
        String reply = aiService.chat(userId, req.prompt(), req.context());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("reply", reply)));
    }

    private AiConversation.Role roleOf(AppUserDetails me) {
        boolean admin = me.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_OWNER") || role.equals("ROLE_STAFF"));
        return admin ? AiConversation.Role.ADMIN : AiConversation.Role.CUSTOMER;
    }

    public record ChatRequest(@NotBlank String prompt, String context) {}
}
