package vn.glassliving.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiRequest(
        Content systemInstruction,
        List<Content> contents,
        GenerationConfig generationConfig
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(String role, List<Part> parts) {
        public static Content system(String text) {
            return new Content(null, List.of(new Part(text)));
        }

        public static Content user(String text) {
            return new Content("user", List.of(new Part(text)));
        }

        public static Content model(String text) {
            return new Content("model", List.of(new Part(text)));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(String text) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerationConfig(Double temperature, Integer maxOutputTokens) {}
}
