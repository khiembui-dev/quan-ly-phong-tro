package vn.glassliving.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import vn.glassliving.ai.dto.GeminiRequest;
import vn.glassliving.ai.dto.GeminiResponse;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class GeminiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final List<String> fallbackModels;

    @Autowired
    public GeminiClient(RestClient.Builder restClientBuilder,
                        @Value("${gemini.api.key:}") String apiKey,
                        @Value("${gemini.api-url:}") String apiUrl,
                        @Value("${gemini.model:gemini-2.5-pro}") String model,
                        @Value("${gemini.fallback-models:gemini-2.5-pro,gemini-2.5-flash,gemini-2.5-flash-lite}") String fallbackModels) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(20_000);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.fallbackModels = parseFallbackModels(fallbackModels);
    }

    protected GeminiClient(RestClient restClient, String apiKey, String apiUrl) {
        this(restClient, apiKey, apiUrl, "gemini-2.5-pro",
                List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite"));
    }

    protected GeminiClient(RestClient.Builder restClientBuilder, String apiKey, String apiUrl) {
        this(restClientBuilder.build(), apiKey, apiUrl);
    }

    protected GeminiClient(RestClient restClient,
                           String apiKey,
                           String apiUrl,
                           String model,
                           List<String> fallbackModels) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.fallbackModels = fallbackModels != null ? fallbackModels : List.of();
    }

    public Optional<String> generate(GeminiRequest request) {
        if (apiKey == null || apiKey.isBlank() || apiUrl == null || apiUrl.isBlank()) {
            return Optional.empty();
        }
        for (String candidateModel : modelOrder()) {
            Optional<String> response = generateWithModel(request, candidateModel);
            if (response.isPresent()) {
                return response;
            }
        }
        return Optional.empty();
    }

    public String configuredModel() {
        return model != null && !model.isBlank() ? model.trim() : "unconfigured";
    }

    private Optional<String> generateWithModel(GeminiRequest request, String candidateModel) {
        String url = urlForModel(candidateModel);
        try {
            log.info("Gemini request model={}", candidateModel);
            GeminiResponse response = restClient.post()
                    .uri(url)
                    .header("x-goog-api-key", apiKey.trim())
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(GeminiResponse.class);
            String text = response != null ? response.firstText() : null;
            return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.trim());
        } catch (RestClientResponseException ex) {
            log.warn("Gemini request failed model={} status={}: {}", candidateModel, ex.getStatusCode().value(), safeMessage(ex));
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                return Optional.empty();
            }
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Gemini request failed model={}: {}", candidateModel, ex.getMessage());
            return Optional.empty();
        }
    }

    private List<String> modelOrder() {
        Set<String> order = new LinkedHashSet<>();
        if (model != null && !model.isBlank()) {
            order.add(model.trim());
        }
        fallbackModels.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(order::add);
        if (!supportsModelFallback()) {
            return order.stream().limit(1).toList();
        }
        return List.copyOf(order);
    }

    private String urlForModel(String candidateModel) {
        String cleanUrl = apiUrl.trim();
        if (candidateModel == null || candidateModel.isBlank()) {
            return cleanUrl;
        }
        return cleanUrl.replaceFirst("/models/[^/:]+:generateContent", "/models/" + candidateModel.trim() + ":generateContent");
    }

    private boolean supportsModelFallback() {
        return apiUrl != null && apiUrl.trim().matches(".*?/models/[^/:]+:generateContent.*");
    }

    private static List<String> parseFallbackModels(String value) {
        if (value == null || value.isBlank()) {
            return List.of("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite");
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String safeMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return ex.getMessage();
        }
        return body.length() <= 240 ? body : body.substring(0, 240);
    }
}
