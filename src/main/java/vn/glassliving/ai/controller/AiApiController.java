package vn.glassliving.ai.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, String>>> chat(@RequestBody ChatRequest req,
                                                                  @AuthenticationPrincipal AppUserDetails me) {
        UUID userId = me != null ? me.getId() : null;
        String reply = aiService.chat(userId != null ? userId : UUID.fromString("00000000-0000-0000-0000-000000000000"),
                req.prompt(), req.context());
        return ResponseEntity.ok(ApiResponse.ok(Map.of("reply", reply)));
    }

    public record ChatRequest(@NotBlank String prompt, String context) {}
}
