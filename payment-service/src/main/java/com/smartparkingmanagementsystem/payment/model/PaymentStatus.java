package com.smartparkingmanagementsystem.payment.model;

/**
 * Outcome of a mock payment transaction.
 * <ul>
 *   <li>{@code PENDING} - reserved for future asynchronous processing; the mock
 *       gateway currently settles synchronously to SUCCESS or FAILED.</li>
 *   <li>{@code SUCCESS} - the mock gateway accepted the payment.</li>
 *   <li>{@code FAILED} - the mock gateway declined the payment (e.g. the
 *       configured mock-failed card number).</li>
 * </ul>
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
