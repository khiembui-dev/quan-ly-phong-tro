package vn.glassliving.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.glassliving.ai.dto.AiChatRequest;
import vn.glassliving.ai.dto.AiChatResponse;
import vn.glassliving.ai.dto.GeminiRequest;
import vn.glassliving.ai.entity.AiConversation;
import vn.glassliving.ai.entity.AiMessage;
import vn.glassliving.ai.repository.AiConversationRepository;
import vn.glassliving.ai.repository.AiMessageRepository;
import vn.glassliving.common.exception.BusinessException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final GeminiClient geminiClient;
    private final AiToolService toolService;
    private final IntentDetector intentDetector;
    @Autowired(required = false)
    private GeminiIntentPlanner geminiIntentPlanner;

    @Transactional
    public AiChatResponse chat(UUID userId, AiConversation.Role role, AiChatRequest request) {
        if (userId == null) throw BusinessException.unauthorized("Yêu cầu đăng nhập.");
        String message = request != null ? cleanMessage(request.message()) : "";
        if (message.isBlank()) throw BusinessException.badRequest("Vui lòng nhập nội dung.");
        if (message.length() > 1000) throw BusinessException.badRequest("Tin nhắn tối đa 1000 ký tự.");

        AiConversation conversation = resolveConversation(userId, role, request.conversationId(), message);
        DetectedIntent detectedIntent = detectIntent(role, message);
        AiToolService.ToolContext toolContext = toolService.buildContext(userId, role, detectedIntent, message);

        log.info("AI chat userId={} role={} intent={} toolName={} dataFound={} model={}",
                userId,
                role,
                toolContext.intent(),
                toolContext.toolName(),
                toolContext.dataFound(),
                geminiClient.configuredModel());

        saveMessage(conversation.getId(), AiMessage.Sender.USER, message);

        String answer = answerFromToolOrGemini(role, conversation, message, toolContext);

        saveMessage(conversation.getId(), AiMessage.Sender.AI, answer);
        conversation.setUpdatedAt(OffsetDateTime.now());
        conversationRepository.save(conversation);
        return AiChatResponse.ok(
                conversation.getId(),
                answer,
                toolContext.suggestions(),
                toolContext.sourceSummary(),
                toolContext.dataFound());
    }

    void setGeminiIntentPlanner(GeminiIntentPlanner geminiIntentPlanner) {
        this.geminiIntentPlanner = geminiIntentPlanner;
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> listConversations(UUID userId, AiConversation.Role role) {
        return conversationRepository.findTop20ByUserIdAndRoleOrderByUpdatedAtDesc(userId, role)
                .stream()
                .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationDetail getConversation(UUID userId, AiConversation.Role role, UUID conversationId) {
        AiConversation conversation = conversationRepository.findByIdAndUserIdAndRole(conversationId, userId, role)
                .orElseThrow(() -> BusinessException.notFound("Cuộc trò chuyện"));
        List<MessageDto> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream()
                .map(m -> new MessageDto(m.getSender().name(), m.getContent(), m.getCreatedAt()))
                .toList();
        return new ConversationDetail(conversation.getId(), conversation.getTitle(), messages);
    }

    @Transactional
    public void deleteConversation(UUID userId, AiConversation.Role role, UUID conversationId) {
        AiConversation conversation = conversationRepository.findByIdAndUserIdAndRole(conversationId, userId, role)
                .orElseThrow(() -> BusinessException.notFound("Cuộc trò chuyện"));
        conversationRepository.delete(conversation);
    }

    private String answerFromToolOrGemini(AiConversation.Role role,
                                          AiConversation conversation,
                                          String message,
                                          AiToolService.ToolContext toolContext) {
        if (hasText(toolContext.directAnswer())) {
            return toolContext.directAnswer().trim();
        }
        if (!toolContext.inScope()) {
            return "Em không thể hỗ trợ nội dung này. Em có thể hướng dẫn anh sử dụng SmartRent hoặc xử lý các vấn đề trong hệ thống.";
        }
        if (toolContext.requiresData() && !toolContext.dataFound()) {
            return "Hiện hệ thống chưa có dữ liệu này. Anh có thể kiểm tra lại hoặc gửi ticket hỗ trợ.";
        }

        List<AiMessage> history = messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversation.getId());
        List<GeminiRequest.Content> contents = toGeminiContents(history, message);
        GeminiRequest geminiRequest = new GeminiRequest(
                GeminiRequest.Content.system(systemPrompt(role) + "\n\n" + toolContext.context()),
                contents,
                new GeminiRequest.GenerationConfig(0.15, 700)
        );
        return geminiClient.generate(geminiRequest)
                .orElseGet(() -> fallbackAnswer(role, toolContext));
    }

    private DetectedIntent detectIntent(AiConversation.Role role, String message) {
        if (geminiIntentPlanner != null) {
            try {
                Optional<DetectedIntent> planned = geminiIntentPlanner.plan(role, message);
                if (planned.isPresent()) {
                    return planned.get();
                }
            } catch (Exception ex) {
                log.warn("AI intent planner failed role={}: {}", role, ex.getMessage());
            }
        }
        return intentDetector.detect(role, message);
    }

    private AiConversation resolveConversation(UUID userId,
                                               AiConversation.Role role,
                                               UUID requestedId,
                                               String firstMessage) {
        if (requestedId != null) {
            Optional<AiConversation> existing = conversationRepository.findByIdAndUserIdAndRole(requestedId, userId, role);
            if (existing.isPresent()) return existing.get();
        }
        AiConversation conversation = AiConversation.builder()
                .userId(userId)
                .role(role)
                .title(titleFrom(firstMessage))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        return conversationRepository.save(conversation);
    }

    private void saveMessage(UUID conversationId, AiMessage.Sender sender, String content) {
        messageRepository.save(AiMessage.builder()
                .conversationId(conversationId)
                .sender(sender)
                .content(content)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    private List<GeminiRequest.Content> toGeminiContents(List<AiMessage> historyDesc, String currentMessage) {
        List<AiMessage> history = new ArrayList<>(historyDesc != null ? historyDesc : List.of());
        Collections.reverse(history);
        List<GeminiRequest.Content> contents = new ArrayList<>();
        for (AiMessage item : history) {
            if (item.getSender() == AiMessage.Sender.USER) {
                contents.add(GeminiRequest.Content.user(item.getContent()));
            } else if (item.getSender() == AiMessage.Sender.AI) {
                contents.add(GeminiRequest.Content.model(item.getContent()));
            }
        }
        if (contents.isEmpty() || !Objects.equals(lastText(contents), currentMessage)) {
            contents.add(GeminiRequest.Content.user(currentMessage));
        }
        return contents;
    }

    private String lastText(List<GeminiRequest.Content> contents) {
        if (contents.isEmpty()) return null;
        GeminiRequest.Content last = contents.getLast();
        if (last.parts() == null || last.parts().isEmpty()) return null;
        return last.parts().getFirst().text();
    }

    private String systemPrompt(AiConversation.Role role) {
        String permission = role == AiConversation.Role.ADMIN
                ? "Nếu người dùng là ADMIN, được xem và phân tích dữ liệu quản trị do backend cung cấp."
                : "Nếu người dùng là CUSTOMER, chỉ trả lời dữ liệu tài khoản và dữ liệu của chính khách hàng hiện tại.";
        return """
                Bạn là Trợ lý AI SmartRent, hỗ trợ người dùng sử dụng toàn bộ hệ thống quản lý phòng trọ.
                Bạn có thể hỗ trợ:
                - Hướng dẫn sử dụng website
                - Quản lý phòng
                - Quản lý khách thuê
                - Cơ sở/chi nhánh
                - Hợp đồng/thuê phòng
                - Hóa đơn
                - Thanh toán
                - Điện nước
                - Ticket hỗ trợ
                - Thông báo
                - Tài khoản cá nhân
                - Báo cáo, thống kê nếu người dùng là admin
                - Giải thích lỗi, hướng dẫn thao tác trên giao diện

                Nguyên tắc:
                1. Nếu câu hỏi là hướng dẫn sử dụng, giải thích chức năng hoặc thao tác trên website, hãy trả lời trực tiếp.
                2. Nếu câu hỏi liên quan dữ liệu thật trong hệ thống, chỉ trả lời dựa trên DATA do backend cung cấp.
                3. Nếu DATA rỗng, nói rõ chưa có dữ liệu.
                4. Không tự bịa số tiền, tên phòng, khách thuê, trạng thái hóa đơn, doanh thu.
                5. Customer chỉ được xem dữ liệu của chính họ.
                6. Admin được xem dữ liệu quản trị.
                7. Không tiết lộ dữ liệu người khác.
                8. Không tự ý sửa/xóa dữ liệu nếu chưa có xác nhận.
                9. Trả lời bằng tiếng Việt, thân thiện, ngắn gọn, dễ hiểu.
                %s
                Khi nhắc tiền, format VNĐ.
                Khi nhắc hóa đơn, nêu rõ tháng, phòng, tổng tiền, trạng thái.
                Không dùng markdown quá dài.
                """.formatted(permission);
    }

    private String fallbackAnswer(AiConversation.Role role, AiToolService.ToolContext toolContext) {
        if (toolContext != null && !toolContext.requiresData()) {
            if (role == AiConversation.Role.ADMIN) {
                return "SmartRent có các tiện ích chính: tổng quan hôm nay, quản lý phòng, cơ sở, khách thuê, hóa đơn, thanh toán, điện nước, ticket, thông báo và báo cáo doanh thu. Anh có thể hỏi cách thao tác hoặc hỏi số liệu quản trị để hệ thống lấy dữ liệu thật.";
            }
            return "SmartRent có các tiện ích chính: xem phòng đang thuê, hạn phòng, hóa đơn, thanh toán QR, lịch sử điện nước, gửi ticket, nhận thông báo, đặt phòng trống và liên hệ chủ trọ.";
        }
        if (role == AiConversation.Role.ADMIN) {
            return "AI đang bận, vui lòng thử lại sau. Dữ liệu hệ thống không bị thay đổi.";
        }
        return "AI đang bận, vui lòng thử lại sau. Nếu cần hỗ trợ gấp, anh có thể gửi ticket hoặc liên hệ chủ trọ.";
    }

    private String cleanMessage(String value) {
        return value == null ? "" : value.trim();
    }

    private String titleFrom(String message) {
        String clean = cleanMessage(message).replaceAll("\\s+", " ");
        if (clean.isBlank()) return "Cuộc trò chuyện mới";
        return clean.length() <= 80 ? clean : clean.substring(0, 80);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ConversationSummary(UUID id, String title, OffsetDateTime updatedAt) {}
    public record ConversationDetail(UUID id, String title, List<MessageDto> messages) {}
    public record MessageDto(String sender, String content, OffsetDateTime createdAt) {}
}
