package vn.glassliving.ai.dto;

import java.util.List;

public record GeminiResponse(List<Candidate> candidates) {
    public String firstText() {
        if (candidates == null || candidates.isEmpty()) return null;
        Content content = candidates.getFirst().content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) return null;
        return content.parts().stream()
                .map(Part::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);
    }

    public record Candidate(Content content) {}
    public record Content(List<Part> parts) {}
    public record Part(String text) {}
}
