package vn.glassliving.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import vn.glassliving.ai.dto.GeminiRequest;
import vn.glassliving.ai.entity.AiConversation;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiIntentPlannerTest {

    @Test
    void plansCustomerInvoiceToolFromGeminiJson() {
        StubGeminiClient gemini = new StubGeminiClient("""
                {
                  "intent": "INVOICE",
                  "toolName": "getMyInvoices",
                  "requiresData": true
                }
                """);
        GeminiIntentPlanner planner = new GeminiIntentPlanner(gemini, new ObjectMapper());

        Optional<DetectedIntent> detected = planner.plan(
                AiConversation.Role.CUSTOMER,
                "Hoa don thang nay cua toi bao nhieu?");

        assertThat(detected).isPresent();
        assertThat(detected.get().intent()).isEqualTo(AiIntent.INVOICE);
        assertThat(detected.get().toolName()).isEqualTo("getMyInvoices");
        assertThat(detected.get().requiresData()).isTrue();
        assertThat(detected.get().inScope()).isTrue();
        assertThat(gemini.lastRequest.systemInstruction().parts().getFirst().text())
                .contains("CUSTOMER")
                .contains("getMyInvoices")
                .contains("getRevenueSummary");
    }

    @Test
    void rejectsAdminOnlyToolForCustomer() {
        StubGeminiClient gemini = new StubGeminiClient("""
                {
                  "intent": "REVENUE_SUMMARY",
                  "toolName": "getRevenueSummary",
                  "requiresData": true
                }
                """);
        GeminiIntentPlanner planner = new GeminiIntentPlanner(gemini, new ObjectMapper());

        Optional<DetectedIntent> detected = planner.plan(
                AiConversation.Role.CUSTOMER,
                "Doanh thu thang nay bao nhieu?");

        assertThat(detected).isEmpty();
    }

    @Test
    void returnsEmptyWhenGeminiOutputIsNotValidJson() {
        StubGeminiClient gemini = new StubGeminiClient("Tôi nghĩ đây là câu hỏi về hóa đơn.");
        GeminiIntentPlanner planner = new GeminiIntentPlanner(gemini, new ObjectMapper());

        Optional<DetectedIntent> detected = planner.plan(
                AiConversation.Role.ADMIN,
                "Hoa don nao chua thanh toan?");

        assertThat(detected).isEmpty();
    }

    private static class StubGeminiClient extends GeminiClient {
        private final String nextAnswer;
        private GeminiRequest lastRequest;

        private StubGeminiClient(String nextAnswer) {
            super(RestClient.builder(), "test-key", "https://gemini.example/generate");
            this.nextAnswer = nextAnswer;
        }

        @Override
        public Optional<String> generate(GeminiRequest request) {
            this.lastRequest = request;
            return Optional.ofNullable(nextAnswer);
        }
    }
}
