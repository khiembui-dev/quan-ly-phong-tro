package vn.glassliving.common.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class I18nWebControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void englishBundleExposesMessagesAndLegacyCustomerText() {
        I18nWebController controller = new I18nWebController();

        Map<String, Object> bundle = controller.bundle("en");

        assertThat(bundle).containsEntry("language.en", "English");
        assertThat(bundle).containsEntry("action.login", "Log in");
        assertThat(bundle).containsEntry("home.hero.titlePrefix", "Find rental rooms");
        assertThat(bundle).containsEntry("home.rooms.title", "Available rooms");
        assertThat(bundle).containsEntry("home.testimonials.title", "Finding a place to call home");
        assertThat(bundle).containsEntry("roomCard.perMonth", "/ month");
        assertThat(bundle).containsKey("legacy");

        Map<String, Object> legacy = (Map<String, Object>) bundle.get("legacy");
        Map<String, String> text = (Map<String, String>) legacy.get("text");
        assertThat(text)
                .containsEntry("Đăng nhập", "Log in")
                .containsEntry("Đăng ký", "Register")
                .containsEntry("Hồ sơ", "Profile");
    }

    @Test
    void invalidLanguageFallsBackToVietnameseBundle() {
        I18nWebController controller = new I18nWebController();

        Map<String, Object> bundle = controller.bundle("fr");

        assertThat(bundle).containsEntry("language.en", "English");
        assertThat(bundle).containsEntry("language.vi", "Tiếng Việt");
    }
}
