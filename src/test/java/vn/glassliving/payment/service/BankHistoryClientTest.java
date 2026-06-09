package vn.glassliving.payment.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.glassliving.payment.config.PaymentBankProperties;
import vn.glassliving.payment.dto.BankHistoryResponseDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BankHistoryClientTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(BankHistoryClient.class, PaymentBankProperties.class)
            .withPropertyValues("app.payment.bank.api-url=https://bank.example/history");

    @Test
    void springContextCanCreateBankHistoryClient() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(BankHistoryClient.class));
    }

    @Test
    void bankHistoryUrlAliasCanConfigurePaymentBankClient() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withUserConfiguration(BankHistoryClient.class, PaymentBankProperties.class)
                .withPropertyValues("app.payment.bank.api-url=https://bank.example/from-legacy-env")
                .run(context -> assertThat(context.getBean(PaymentBankProperties.class).getApiUrl())
                        .isEqualTo("https://bank.example/from-legacy-env"));
    }

    @Test
    void fetchHistoryReadsConfiguredBankApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentBankProperties properties = bankProperties("https://bank.example/history");
        BankHistoryClient client = new BankHistoryClient(builder.build(), properties);

        server.expect(requestTo("https://bank.example/history"))
                .andRespond(withSuccess("""
                        {
                          "time": 1780851600000,
                          "codeStatus": 200,
                          "messageStatus": "success",
                          "description": "ok",
                          "data": [
                            {
                              "amount": 850000,
                              "accountName": "39118057",
                              "receiverName": "BUI THE KHIEM",
                              "transactionNumber": "47976",
                              "description": "PT-8",
                              "bankName": "ACB",
                              "isOnline": true,
                              "postingDate": 1780851600000,
                              "accountOwner": "BUI THE KHIEM",
                              "type": "IN",
                              "receiverAccountNumber": "39118057",
                              "currency": "VND",
                              "account": "39118057",
                              "activeDatetime": 1780752311000,
                              "effectiveDate": "2026-06-08"
                            }
                          ],
                          "redisTook": 2
                        }
                        """, MediaType.APPLICATION_JSON));

        BankHistoryResponseDto response = client.fetchHistory().orElseThrow();

        assertThat(response.codeStatus()).isEqualTo(200);
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().getFirst().amount()).isEqualByComparingTo("850000");
        assertThat(response.data().getFirst().transactionNumber()).isEqualTo("47976");
        assertThat(response.data().getFirst().description()).isEqualTo("PT-8");
        server.verify();
    }

    @Test
    void fetchHistoryReturnsEmptyWhenBankApiFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentBankProperties properties = bankProperties("https://bank.example/history");
        BankHistoryClient client = new BankHistoryClient(builder.build(), properties);

        server.expect(requestTo("https://bank.example/history")).andRespond(withServerError());

        assertThat(client.fetchHistory()).isEmpty();
        server.verify();
    }

    private PaymentBankProperties bankProperties(String apiUrl) {
        PaymentBankProperties properties = new PaymentBankProperties();
        properties.setApiUrl(apiUrl);
        properties.setCode("ACB");
        properties.setAccountNumber("39118057");
        properties.setAccountName("BUI THE KHIEM");
        properties.setDisplayName("Ngân hàng ACB");
        return properties;
    }
}
