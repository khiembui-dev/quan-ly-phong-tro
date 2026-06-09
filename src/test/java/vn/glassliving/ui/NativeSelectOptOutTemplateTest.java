package vn.glassliving.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NativeSelectOptOutTemplateTest {

    @Test
    void systemSettingsSecuritySelectUsesNativeDropdown() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/templates/admin/automations.html"));

        assertThat(source).contains("x-ref=\"smtpSecurity\"");
        assertThat(source).contains("data-native-select");
    }

    @Test
    void customerTicketModalUsesNativeDropdownsInsideModal() throws IOException {
        String source = Files.readString(Path.of("src/main/resources/templates/customer/notifications.html"));

        assertThat(source).contains("<select name=\"roomId\" required data-native-select>");
        assertThat(source).contains("<select name=\"category\" data-native-select>");
        assertThat(source).contains("<select name=\"priority\" data-native-select>");
    }
}
