package vn.glassliving.payment.dto;

import java.util.List;

public record BankHistoryResponseDto(
        Integer codeStatus,
        String messageStatus,
        List<BankTransactionDto> data
) {}
