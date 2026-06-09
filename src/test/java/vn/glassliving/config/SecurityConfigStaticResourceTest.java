package vn.glassliving.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigStaticResourceTest {

    @Test
    void roomUploadsArePublicButOtherUploadsStayAuthenticated() throws IOException {
        String source = Files.readString(Path.of("src/main/java/vn/glassliving/config/SecurityConfig.java"));

        assertThat(source).contains("\"/uploads/rooms/**\"");
        assertThat(source).contains(".requestMatchers(\"/uploads/**\").authenticated()");
        assertThat(source.indexOf("\"/uploads/rooms/**\""))
                .isLessThan(source.indexOf(".requestMatchers(\"/uploads/**\").authenticated()"));
    }
}
