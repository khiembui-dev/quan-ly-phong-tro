package vn.glassliving.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID roomId,
        String fullName,
        String cccd,
        String phone,
        String email,
        String address,
        String occupation,
        String workplace,
        LocalDate moveInDate,
        Short duration,
        Short occupants,
        Boolean hasPet,
        String note,
        String paymentMethod
) {}
