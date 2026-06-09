package vn.glassliving.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiChatResponse(
        boolean success,
        UUID conversationId,
        String message,
        List<String> suggestions,
        String sourceSummary,
        boolean dataFound
) {
    public AiChatResponse(boolean success, UUID conversationId, String message, List<String> suggestions) {
        this(success, conversationId, message, suggestions, null, true);
    }

    public static AiChatResponse ok(UUID conversationId, String message, List<String> suggestions) {
        return ok(conversationId, message, suggestions, null, true);
    }

    public static AiChatResponse ok(UUID conversationId,
                                    String message,
                                    List<String> suggestions,
                                    String sourceSummary,
                                    boolean dataFound) {
        return new AiChatResponse(
                true,
                conversationId,
                message,
                suggestions != null ? suggestions : List.of(),
                sourceSummary,
                dataFound);
    }
}
