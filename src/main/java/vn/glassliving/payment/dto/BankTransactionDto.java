package vn.glassliving.payment.dto;

import java.math.BigDecimal;

public record BankTransactionDto(
        BigDecimal amount,
        String accountName,
        String receiverName,
        String transactionNumber,
        String description,
        String bankName,
        String type,
        String currency,
        Long postingDate,
        Long activeDatetime
) {}
