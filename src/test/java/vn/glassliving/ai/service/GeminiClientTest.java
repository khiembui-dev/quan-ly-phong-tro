package vn.glassliving.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.glassliving.ai.dto.GeminiRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

    @Test
    void defaultModelUsesGemini25ProForProductionChat() {
        GeminiClient client = new GeminiClient(RestClient.builder().build(), "test-key", "https://gemini.example/generate");

        assertThat(client.configuredModel()).isEqualTo("gemini-2.5-pro");
    }

    @Test
    void generateSendsApiKeyHeaderAndParsesText() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient client = new GeminiClient(builder.build(), "test-key", "https://gemini.example/generate");
        GeminiRequest request = new GeminiRequest(
                new GeminiRequest.Content(null, List.of(new GeminiRequest.Part("system"))),
                List.of(new GeminiRequest.Content("user", List.of(new GeminiRequest.Part("Xin chao")))),
                new GeminiRequest.GenerationConfig(0.35, 768)
        );

        server.expect(requestTo("https://gemini.example/generate"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "Hoa don thang nay la 850.000 VND." }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.generate(request)).contains("Hoa don thang nay la 850.000 VND.");
        server.verify();
    }

    @Test
    void generateReturnsEmptyWhenApiKeyMissingOrApiFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient missingKeyClient = new GeminiClient(builder.build(), "", "https://gemini.example/generate");

        assertThat(missingKeyClient.generate(new GeminiRequest(null, List.of(), null))).isEmpty();

        GeminiClient failingClient = new GeminiClient(builder.build(), "test-key", "https://gemini.example/generate");
        server.expect(requestTo("https://gemini.example/generate")).andRespond(withServerError());

        assertThat(failingClient.generate(new GeminiRequest(null, List.of(), null))).isEmpty();
        server.verify();
    }

    @Test
    void generateFallsBackToNextModelWhenPrimaryIsUnsupported() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiClient client = new GeminiClient(
                builder.build(),
                "test-key",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
                "gemini-3.5-flash",
                List.of("gemini-3.5-flash", "gemini-2.5-flash"));
        GeminiRequest request = new GeminiRequest(
                new GeminiRequest.Content(null, List.of(new GeminiRequest.Part("system"))),
                List.of(new GeminiRequest.Content("user", List.of(new GeminiRequest.Part("Xin chao")))),
                new GeminiRequest.GenerationConfig(0.35, 768)
        );

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "Da fallback model." }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.generate(request)).contains("Da fallback model.");
        server.verify();
    }
}
