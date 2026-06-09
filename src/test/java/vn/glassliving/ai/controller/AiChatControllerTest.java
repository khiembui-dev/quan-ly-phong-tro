package vn.glassliving.ai.controller;

import org.junit.jupiter.api.Test;
import vn.glassliving.ai.dto.AiChatRequest;
import vn.glassliving.ai.dto.AiChatResponse;
import vn.glassliving.ai.entity.AiConversation;
import vn.glassliving.ai.service.AiChatService;
import vn.glassliving.auth.entity.User;
import vn.glassliving.auth.security.AppUserDetails;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatControllerTest {

    @Test
    void chatUsesAuthenticatedCustomerIdentity() {
        UUID customerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        CapturingAiChatService service = new CapturingAiChatService(conversationId);
        AiChatController controller = new AiChatController(service);

        AiChatResponse response = controller.chat(new AiChatRequest("Phong cua toi", null), principal(customerId, User.Role.TENANT));

        assertThat(response.success()).isTrue();
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(service.userId).isEqualTo(customerId);
        assertThat(service.role).isEqualTo(AiConversation.Role.CUSTOMER);
    }

    @Test
    void chatUsesAuthenticatedAdminRole() {
        UUID adminId = UUID.randomUUID();
        CapturingAiChatService service = new CapturingAiChatService(UUID.randomUUID());
        AiChatController controller = new AiChatController(service);

        controller.chat(new AiChatRequest("Doanh thu", null), principal(adminId, User.Role.ADMIN));

        assertThat(service.userId).isEqualTo(adminId);
        assertThat(service.role).isEqualTo(AiConversation.Role.ADMIN);
    }

    private AppUserDetails principal(UUID id, User.Role role) {
        User user = User.builder()
                .email(role.name().toLowerCase() + "@example.com")
                .fullName(role.name())
                .passwordHash("hash")
                .status(User.UserStatus.ACTIVE)
                .roles(Set.of(role))
                .build();
        user.setId(id);
        return new AppUserDetails(user);
    }

    private static class CapturingAiChatService extends AiChatService {
        private final UUID conversationId;
        private UUID userId;
        private AiConversation.Role role;

        private CapturingAiChatService(UUID conversationId) {
            super(null, null, null, null, null);
            this.conversationId = conversationId;
        }

        @Override
        public AiChatResponse chat(UUID userId, AiConversation.Role role, AiChatRequest request) {
            this.userId = userId;
            this.role = role;
            return new AiChatResponse(true, conversationId, "OK", List.of("Ticket mới"));
        }
    }
}
