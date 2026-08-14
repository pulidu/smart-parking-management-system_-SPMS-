package com.smartparkingmanagementsystem.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.smartparkingmanagementsystem.payment.model.PaymentMethod;
import com.smartparkingmanagementsystem.payment.model.PaymentStatus;

/**
 * Public payment representation. The card number is never exposed - only a
 * masked value (e.g. {@code **** **** **** 1111}) derived from the stored last
 * four digits, or {@code null} for cash / mock-wallet payments.
 */
public record PaymentResponse(
        Long id,
        Long reservationId,
        Long userId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String transactionId,
        PaymentStatus status,
        Instant paymentDate,
        Instant createdAt,
        String maskedCardNumber) {
}
