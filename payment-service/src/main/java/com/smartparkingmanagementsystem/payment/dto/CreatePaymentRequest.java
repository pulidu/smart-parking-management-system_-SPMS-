package com.smartparkingmanagementsystem.payment.dto;

import java.math.BigDecimal;

import com.smartparkingmanagementsystem.payment.model.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Payload for {@code POST /api/payments} (process a mock payment).
 *
 * <p>{@code cardNumber} is only required for {@code CARD} payments (validated in
 * the service layer). When present it must be 13-19 digits; it is never stored -
 * only the last four digits are kept and responses return a masked value. The
 * mock gateway deterministically declines the configured fail card number.
 */
public record CreatePaymentRequest(
        @NotNull(message = "reservationId is required")
        @Positive(message = "reservationId must be a positive number")
        Long reservationId,

        @NotNull(message = "userId is required")
        @Positive(message = "userId must be a positive number")
        Long userId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        BigDecimal amount,

        @NotNull(message = "paymentMethod is required")
        PaymentMethod paymentMethod,

        @Pattern(regexp = "^\\d{13,19}$",
                message = "cardNumber must be 13-19 digits")
        String cardNumber) {
}
