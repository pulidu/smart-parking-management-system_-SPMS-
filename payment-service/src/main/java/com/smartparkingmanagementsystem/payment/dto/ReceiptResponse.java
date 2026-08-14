package com.smartparkingmanagementsystem.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.smartparkingmanagementsystem.payment.model.PaymentMethod;
import com.smartparkingmanagementsystem.payment.model.PaymentStatus;

/**
 * Digital receipt returned by
 * {@code GET /api/payments/{id}/receipt}. The receipt id is derived from the
 * stored payment id so it is stable and reproducible.
 */
public record ReceiptResponse(
        String receiptId,
        String transactionId,
        Long reservationId,
        Long userId,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        Instant paymentDate) {
}
