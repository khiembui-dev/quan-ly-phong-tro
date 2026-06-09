package vn.glassliving.ai.service;

public record DetectedIntent(
        AiIntent intent,
        String toolName,
        boolean requiresData,
        boolean inScope
) {
    public static DetectedIntent of(AiIntent intent, String toolName, boolean requiresData) {
        return new DetectedIntent(intent, toolName, requiresData, true);
    }

    public static DetectedIntent outOfScope(AiIntent intent) {
        return new DetectedIntent(intent, "none", false, false);
    }
}
