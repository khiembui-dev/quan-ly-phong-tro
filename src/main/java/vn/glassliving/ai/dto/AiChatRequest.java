package vn.glassliving.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AiChatRequest(
        @NotBlank
        @Size(max = 1000)
        String message,
        UUID conversationId
) {
}
