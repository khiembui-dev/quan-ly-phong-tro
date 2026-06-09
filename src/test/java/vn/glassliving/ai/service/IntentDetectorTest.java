package vn.glassliving.ai.service;

import org.junit.jupiter.api.Test;
import vn.glassliving.ai.entity.AiConversation;

import static org.assertj.core.api.Assertions.assertThat;

class IntentDetectorTest {

    private final IntentDetector detector = new IntentDetector();

    @Test
    void detectsCustomerIntentsWithVietnameseAccents() {
        assertThat(detector.detect(AiConversation.Role.CUSTOMER, "Hóa đơn tháng này bao nhiêu?").intent())
                .isEqualTo(AiIntent.INVOICE);
        assertThat(detector.detect(AiConversation.Role.CUSTOMER, "Phòng tôi đang ở là phòng nào?").intent())
                .isEqualTo(AiIntent.ROOM);
        assertThat(detector.detect(AiConversation.Role.CUSTOMER, "Tiền điện nước tháng này bao nhiêu?").intent())
                .isEqualTo(AiIntent.UTILITY);
        assertThat(detector.detect(AiConversation.Role.CUSTOMER, "Cho tôi số điện thoại chủ trọ").intent())
                .isEqualTo(AiIntent.LANDLORD_CONTACT);
    }

    @Test
    void detectsGeneralWebsiteHelpWithoutDataRequirement() {
        DetectedIntent features = detector.detect(AiConversation.Role.CUSTOMER, "Có các tiện ích nào?");
        DetectedIntent qrGuide = detector.detect(AiConversation.Role.CUSTOMER, "Cách thanh toán bằng QR");
        DetectedIntent ticketGuide = detector.detect(AiConversation.Role.CUSTOMER, "Cách gửi ticket");

        assertThat(features.intent()).isEqualTo(AiIntent.GENERAL_HELP);
        assertThat(features.requiresData()).isFalse();
        assertThat(features.inScope()).isTrue();
        assertThat(qrGuide.intent()).isEqualTo(AiIntent.PAYMENT);
        assertThat(qrGuide.requiresData()).isFalse();
        assertThat(ticketGuide.intent()).isEqualTo(AiIntent.TICKET);
        assertThat(ticketGuide.requiresData()).isFalse();
    }

    @Test
    void detectsExpandedCustomerWebsiteIntents() {
        assertThat(detector.detect(AiConversation.Role.CUSTOMER, "Thông tin tài khoản của tôi").intent())
                .isEqualTo(AiIntent.CUSTOMER_ACCOUNT);
        assertThat(detector.detect(AiConversation.Role.CUSTOMER, "Hạn phòng của anh").intent())
                .isEqualTo(AiIntent.ROOM);
        assertThat(detector.detect(AiConversation.Role.CUSTOMER, "Cơ sở và địa chỉ phòng").intent())
                .isEqualTo(AiIntent.PROPERTY);
        assertThat(detector.detect(AiConversation.Role.CUSTOMER, "Thông báo mới").intent())
                .isEqualTo(AiIntent.NOTIFICATION);
        assertThat(detector.detect(AiConversation.Role.CUSTOMER, "SmartRent dùng được trên điện thoại không?").intent())
                .isEqualTo(AiIntent.UNKNOWN);
    }

    @Test
    void customerCannotAskAdminRevenue() {
        DetectedIntent detected = detector.detect(AiConversation.Role.CUSTOMER, "Doanh thu tháng này bao nhiêu?");

        assertThat(detected.intent()).isEqualTo(AiIntent.RESTRICTED_ADMIN_DATA);
        assertThat(detected.inScope()).isFalse();
    }

    @Test
    void detectsAdminIntents() {
        assertThat(detector.detect(AiConversation.Role.ADMIN, "Tháng này doanh thu bao nhiêu?").intent())
                .isEqualTo(AiIntent.ADMIN_DASHBOARD);
        assertThat(detector.detect(AiConversation.Role.ADMIN, "Có bao nhiêu phòng trống?").intent())
                .isEqualTo(AiIntent.ADMIN_DASHBOARD);
        assertThat(detector.detect(AiConversation.Role.ADMIN, "Có hóa đơn nào chưa thanh toán?").intent())
                .isEqualTo(AiIntent.INVOICE);
        assertThat(detector.detect(AiConversation.Role.ADMIN, "Ticket mới hôm nay?").intent())
                .isEqualTo(AiIntent.TICKET);
    }
}
