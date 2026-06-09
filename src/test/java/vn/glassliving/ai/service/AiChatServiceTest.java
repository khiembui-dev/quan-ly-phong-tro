package vn.glassliving.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import vn.glassliving.ai.dto.AiChatRequest;
import vn.glassliving.ai.dto.AiChatResponse;
import vn.glassliving.ai.dto.GeminiRequest;
import vn.glassliving.ai.entity.AiConversation;
import vn.glassliving.ai.entity.AiMessage;
import vn.glassliving.ai.repository.AiConversationRepository;
import vn.glassliving.ai.repository.AiMessageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    private final AiConversationRepository conversationRepository = mock(AiConversationRepository.class);
    private final AiMessageRepository messageRepository = mock(AiMessageRepository.class);
    private final StubGeminiClient geminiClient = new StubGeminiClient();
    private final StubToolService toolService = new StubToolService();
    private final IntentDetector intentDetector = new IntentDetector();
    private final AiChatService service = new AiChatService(
            conversationRepository,
            messageRepository,
            geminiClient,
            toolService,
            intentDetector);

    @Test
    void chatCreatesConversationStoresMessagesAndReturnsSuggestions() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        stubNewConversation(conversationId);

        AiChatResponse response = service.chat(userId, AiConversation.Role.CUSTOMER,
                new AiChatRequest("Hoa don thang nay bao nhieu?", null));

        assertThat(response.success()).isTrue();
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.message()).contains("850.000");
        assertThat(geminiClient.lastRequest.systemInstruction().parts().getFirst().text())
                .contains("SYSTEM_CONTEXT")
                .contains("RULE");
        assertThat(response.suggestions()).contains("Thanh toan ngay");
        assertThat(response.sourceSummary()).isEqualTo("Dữ liệu từ hệ thống SmartRent");
    }

    @Test
    void chatDoesNotLoadConversationOwnedByAnotherRole() {
        UUID userId = UUID.randomUUID();
        UUID requestedConversationId = UUID.randomUUID();
        UUID newConversationId = UUID.randomUUID();
        when(conversationRepository.findByIdAndUserIdAndRole(requestedConversationId, userId, AiConversation.Role.ADMIN))
                .thenReturn(Optional.empty());
        stubNewConversation(newConversationId);

        AiChatResponse response = service.chat(userId, AiConversation.Role.ADMIN,
                new AiChatRequest("Thong ke doanh thu", requestedConversationId));

        assertThat(response.conversationId()).isEqualTo(newConversationId);
    }

    @Test
    void chatUsesDirectToolAnswerForTrustedInvoiceData() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        toolService.directAnswer = "Ban co 2 hoa don mo, tong can thanh toan 9.031.000 VND.";
        geminiClient.lastRequest = null;
        stubNewConversation(conversationId);

        AiChatResponse response = service.chat(userId, AiConversation.Role.CUSTOMER,
                new AiChatRequest("Hoa don thang nay bao nhieu?", null));

        assertThat(response.message()).contains("9.031.000");
        assertThat(geminiClient.lastRequest).isNull();
    }

    @Test
    void chatDoesNotCallGeminiWhenRequiredDataIsMissing() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        toolService.forceMissingData = true;
        geminiClient.lastRequest = null;
        stubNewConversation(conversationId);

        AiChatResponse response = service.chat(userId, AiConversation.Role.CUSTOMER,
                new AiChatRequest("Hoa don thang nay bao nhieu?", null));

        assertThat(response.message()).contains("Hiện hệ thống chưa có dữ liệu này");
        assertThat(response.dataFound()).isFalse();
        assertThat(response.sourceSummary()).isEqualTo("Dữ liệu từ hệ thống SmartRent");
        assertThat(geminiClient.lastRequest).isNull();
    }

    @Test
    void chatBlocksCustomerAdminDataBeforeGemini() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        geminiClient.lastRequest = null;
        stubNewConversation(conversationId);

        AiChatResponse response = service.chat(userId, AiConversation.Role.CUSTOMER,
                new AiChatRequest("Doanh thu thang nay bao nhieu?", null));

        assertThat(response.message()).contains("Thông tin doanh thu chỉ dành cho tài khoản quản trị");
        assertThat(response.dataFound()).isFalse();
        assertThat(geminiClient.lastRequest).isNull();
    }

    @Test
    void chatAllowsGeneralWebsiteHelpThroughGemini() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        geminiClient.lastRequest = null;
        geminiClient.nextAnswer = "SmartRent có các tiện ích chính: xem phòng đang thuê, hóa đơn, thanh toán QR, điện nước, ticket và thông báo.";
        stubNewConversation(conversationId);

        AiChatResponse response = service.chat(userId, AiConversation.Role.CUSTOMER,
                new AiChatRequest("Co cac tien ich nao?", null));

        assertThat(response.message()).contains("SmartRent có các tiện ích chính");
        assertThat(response.sourceSummary()).isEqualTo("Hướng dẫn SmartRent");
        assertThat(response.dataFound()).isFalse();
        assertThat(geminiClient.lastRequest).isNotNull();
        assertThat(geminiClient.lastRequest.systemInstruction().parts().getFirst().text())
                .contains("Hướng dẫn sử dụng website")
                .contains("Không tự bịa");
    }

    @Test
    void chatAllowsUnknownWebsiteHelpThroughGeminiInsteadOfOldScopeRejection() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        geminiClient.lastRequest = null;
        geminiClient.nextAnswer = "SmartRent có thể dùng trên trình duyệt máy tính và điện thoại nếu giao diện được mở bằng tài khoản của anh.";
        stubNewConversation(conversationId);

        AiChatResponse response = service.chat(userId, AiConversation.Role.CUSTOMER,
                new AiChatRequest("SmartRent dung duoc tren dien thoai khong?", null));

        assertThat(response.message()).doesNotContain("Em chỉ hỗ trợ");
        assertThat(response.sourceSummary()).isEqualTo("Hướng dẫn SmartRent");
        assertThat(geminiClient.lastRequest).isNotNull();
    }

    @Test
    void chatFallsBackToWebsiteGuideWhenGeminiIsUnavailableForGeneralHelp() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        geminiClient.lastRequest = null;
        geminiClient.nextAnswer = null;
        stubNewConversation(conversationId);

        AiChatResponse response = service.chat(userId, AiConversation.Role.CUSTOMER,
                new AiChatRequest("Co cac tien ich nao?", null));

        assertThat(response.message()).contains("SmartRent có các tiện ích chính");
        assertThat(response.message()).doesNotContain("AI đang bận");
        assertThat(response.sourceSummary()).isEqualTo("Hướng dẫn SmartRent");
        assertThat(geminiClient.lastRequest).isNotNull();
    }

    @Test
    void chatUsesGeminiPlannerBeforeKeywordDetector() {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        service.setGeminiIntentPlanner(new StubIntentPlanner(
                DetectedIntent.of(AiIntent.CUSTOMER_SEARCH, "searchCustomer", true)));
        stubNewConversation(conversationId);

        AiChatResponse response = service.chat(userId, AiConversation.Role.ADMIN,
                new AiChatRequest("Tim khach co so dien thoai 0901", null));

        assertThat(toolService.lastDetectedIntent.intent()).isEqualTo(AiIntent.CUSTOMER_SEARCH);
        assertThat(toolService.lastDetectedIntent.toolName()).isEqualTo("searchCustomer");
        assertThat(response.success()).isTrue();
    }

    private void stubNewConversation(UUID conversationId) {
        when(conversationRepository.save(any(AiConversation.class))).thenAnswer(invocation -> {
            AiConversation conversation = invocation.getArgument(0);
            conversation.setId(conversationId);
            return conversation;
        });
        when(messageRepository.save(any(AiMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId)).thenReturn(List.of());
    }

    private static class StubGeminiClient extends GeminiClient {
        private GeminiRequest lastRequest;
        private String nextAnswer = "Anh dang co hoa don thang 06/2026, tong tien 850.000 VND.";

        private StubGeminiClient() {
            super(RestClient.builder(), "test-key", "https://gemini.example/generate");
        }

        @Override
        public Optional<String> generate(GeminiRequest request) {
            this.lastRequest = request;
            if (nextAnswer == null) return Optional.empty();
            return Optional.of(nextAnswer);
        }
    }

    private static class StubToolService extends AiToolService {
        private String directAnswer;
        private boolean forceMissingData;
        private DetectedIntent lastDetectedIntent;

        private StubToolService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public ToolContext buildContext(UUID userId, AiConversation.Role role, DetectedIntent detectedIntent, String message) {
            this.lastDetectedIntent = detectedIntent;
            if (!detectedIntent.inScope()) {
                return ToolContext.outOfScope(detectedIntent,
                        "Thông tin doanh thu chỉ dành cho tài khoản quản trị.",
                        new ArrayList<>(List.of("Gui ticket ho tro")));
            }
            if (!detectedIntent.requiresData()) {
                return new ToolContext(
                        "SYSTEM_CONTEXT:\nRole: " + role + "\nIntent: " + detectedIntent.intent() + "\nDATA:\n- Hướng dẫn sử dụng website SmartRent\nRULE:\nTrả lời hướng dẫn thao tác, không tự bịa dữ liệu hệ thống.",
                        new ArrayList<>(List.of("Chatbot làm được gì?", "Cách gửi ticket")),
                        null,
                        detectedIntent,
                        false,
                        "Hướng dẫn SmartRent");
            }
            if (forceMissingData) {
                return ToolContext.noData(detectedIntent,
                        "SYSTEM_CONTEXT:\nRole: CUSTOMER\nIntent: MY_INVOICE\nDATA:\n[]",
                        new ArrayList<>(List.of("Gui ticket ho tro")));
            }
            if (directAnswer != null) {
                return new ToolContext(
                        "SYSTEM_CONTEXT:\nRole: CUSTOMER\nIntent: MY_INVOICE\nDATA:\nHoa don: 850.000 VND\nRULE:\nChi duoc tra loi dua tren DATA.",
                        new ArrayList<>(List.of("Thanh toan ngay")),
                        directAnswer,
                        detectedIntent,
                        true,
                        "Dữ liệu từ hệ thống SmartRent");
            }
            return new ToolContext(
                    "SYSTEM_CONTEXT:\nRole: CUSTOMER\nIntent: MY_INVOICE\nDATA:\nHoa don: 850.000 VND\nRULE:\nChi duoc tra loi dua tren DATA.",
                    new ArrayList<>(List.of("Xem chi tiet hoa don", "Thanh toan ngay")),
                    null,
                    detectedIntent,
                    true,
                    "Dữ liệu từ hệ thống SmartRent");
        }
    }

    private static class StubIntentPlanner extends GeminiIntentPlanner {
        private final DetectedIntent detectedIntent;

        private StubIntentPlanner(DetectedIntent detectedIntent) {
            super(null, null);
            this.detectedIntent = detectedIntent;
        }

        @Override
        public Optional<DetectedIntent> plan(AiConversation.Role role, String message) {
            return Optional.ofNullable(detectedIntent);
        }
    }
}
