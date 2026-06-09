package vn.glassliving.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.payment.bank")
public class PaymentBankProperties {
    private String apiUrl;
    private String code = "ACB";
    private String accountNumber = "39118057";
    private String accountName = "BUI THE KHIEM";
    private String displayName = "ACB";

    public boolean hasApiUrl() {
        return apiUrl != null && !apiUrl.isBlank();
    }
}
