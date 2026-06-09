package vn.glassliving.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentCheckResponseDto(
        String status,
        String message,
        BigDecimal amount,
        OffsetDateTime paidAt
) {
    public static PaymentCheckResponseDto pending() {
        return new PaymentCheckResponseDto(
                "PENDING",
                "Chưa tìm thấy giao dịch, vui lòng chờ 1-3 phút.",
                null,
                null);
    }

    public static PaymentCheckResponseDto paid(BigDecimal amount, OffsetDateTime paidAt) {
        return new PaymentCheckResponseDto(
                "PAID",
                "Thanh toán thành công.",
                amount,
                paidAt);
    }

    public static PaymentCheckResponseDto bankApiError() {
        return new PaymentCheckResponseDto(
                "BANK_API_ERROR",
                "Chưa thể kiểm tra ngân hàng, vui lòng thử lại.",
                null,
                null);
    }
}
