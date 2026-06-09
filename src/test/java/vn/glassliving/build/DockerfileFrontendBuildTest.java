package vn.glassliving.build;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DockerfileFrontendBuildTest {

    @Test
    void frontendStageCopiesJavascriptBeforeTailwindBuild() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertThat(dockerfile).contains("COPY src/main/resources/static/js ./src/main/resources/static/js");
        assertThat(dockerfile.indexOf("COPY src/main/resources/static/js"))
                .isLessThan(dockerfile.indexOf("RUN npm run build:css"));
    }
}
