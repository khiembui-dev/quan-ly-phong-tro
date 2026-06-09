package vn.glassliving.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.glassliving.payment.config.PaymentBankProperties;
import vn.glassliving.payment.dto.BankHistoryResponseDto;

import java.util.Optional;

@Service
public class BankHistoryClient {

    private static final Logger log = LoggerFactory.getLogger(BankHistoryClient.class);

    private final RestClient restClient;
    private final PaymentBankProperties properties;

    @Autowired
    public BankHistoryClient(RestClient.Builder restClientBuilder, PaymentBankProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    protected BankHistoryClient(RestClient restClient, PaymentBankProperties properties) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public Optional<BankHistoryResponseDto> fetchHistory() {
        if (!properties.hasApiUrl()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(properties.getApiUrl().trim())
                    .retrieve()
                    .body(BankHistoryResponseDto.class));
        } catch (RestClientException ex) {
            log.warn("Bank history API check failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
