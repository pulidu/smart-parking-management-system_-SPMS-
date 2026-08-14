package com.smartparkingmanagementsystem.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartparkingmanagementsystem.payment.dto.CreatePaymentRequest;
import com.smartparkingmanagementsystem.payment.dto.PaymentResponse;
import com.smartparkingmanagementsystem.payment.dto.ReceiptResponse;
import com.smartparkingmanagementsystem.payment.exception.DuplicatePaymentException;
import com.smartparkingmanagementsystem.payment.exception.InvalidPaymentDataException;
import com.smartparkingmanagementsystem.payment.exception.PaymentNotFoundException;
import com.smartparkingmanagementsystem.payment.model.Payment;
import com.smartparkingmanagementsystem.payment.model.PaymentMethod;
import com.smartparkingmanagementsystem.payment.model.PaymentStatus;
import com.smartparkingmanagementsystem.payment.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business logic for the MOCK payment gateway.
 *
 * <p>The gateway never integrates with a real provider. It validates mock card
 * data, verifies the reservation exists (via the parking service), guards
 * against duplicate payments and settles each transaction deterministically:
 * <ul>
 *   <li>{@code CARD} payments succeed unless the card number equals the
 *       configured mock-failed card (default {@code 4000000000000002});</li>
 *   <li>{@code CASH} and {@code MOCK_WALLET} always succeed.</li>
 * </ul>
 * A failed transaction is still stored so it can be audited; a retry for the
 * same reservation is allowed after a failure.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final List<PaymentStatus> BLOCKING_STATUSES =
            List.of(PaymentStatus.PENDING, PaymentStatus.SUCCESS);

    private static final String CARD_PATTERN = "^\\d{13,19}$";

    private final PaymentRepository paymentRepository;
    private final ReservationVerifier reservationVerifier;

    @Value("${payment-service.mock-failed-card:4000000000000002}")
    private String mockFailedCard;

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        String cardNumber = request.cardNumber();

        validateCardForMethod(request.paymentMethod(), cardNumber);

        // 1. The referenced reservation must exist (404 if it does not).
        reservationVerifier.verifyExists(request.reservationId());

        // 2. Prevent a duplicate payment for the same reservation (409).
        if (paymentRepository.existsByReservationIdAndStatusIn(request.reservationId(), BLOCKING_STATUSES)) {
            throw new DuplicatePaymentException(
                    "A payment already exists for reservation " + request.reservationId());
        }

        // 3. Mock gateway settles the transaction.
        PaymentStatus status = simulateGateway(request.paymentMethod(), cardNumber);

        Payment payment = new Payment();
        payment.setReservationId(request.reservationId());
        payment.setUserId(request.userId());
        payment.setAmount(request.amount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setTransactionId(generateTransactionId());
        payment.setStatus(status);
        payment.setPaymentDate(Instant.now());
        if (cardNumber != null) {
            payment.setCardLast4(last4(cardNumber));
        }

        return PaymentResponseMapper.toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(Long id) {
        return PaymentResponseMapper.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByReservation(Long reservationId) {
        return paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
                .map(PaymentResponseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listByUser(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PaymentResponseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(Long id) {
        Payment payment = findById(id);
        return new ReceiptResponse(
                "RCPT-" + payment.getId(),
                payment.getTransactionId(),
                payment.getReservationId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getPaymentDate());
    }

    private void validateCardForMethod(PaymentMethod method, String cardNumber) {
        if (method == PaymentMethod.CARD && (cardNumber == null || cardNumber.isBlank())) {
            throw new InvalidPaymentDataException("cardNumber is required for CARD payments");
        }
        if (cardNumber == null) {
            return;
        }
        String trimmed = cardNumber.trim();
        if (!trimmed.matches(CARD_PATTERN)) {
            throw new InvalidPaymentDataException("cardNumber must be 13-19 digits");
        }
        if (!luhnValid(trimmed)) {
            throw new InvalidPaymentDataException("cardNumber failed the Luhn check");
        }
    }

    private PaymentStatus simulateGateway(PaymentMethod method, String cardNumber) {
        if (method == PaymentMethod.CARD && cardNumber != null && cardNumber.equals(mockFailedCard)) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.SUCCESS;
    }

    /**
     * Standard Luhn checksum - the "basic format" validation for mock cards.
     */
    private static boolean luhnValid(String digits) {
        int sum = 0;
        boolean alternate = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private static String last4(String cardNumber) {
        return cardNumber.substring(cardNumber.length() - 4);
    }

    private static String generateTransactionId() {
        return String.format("TXN-%d-%06d",
                System.currentTimeMillis(), ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private Payment findById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));
    }

    /**
     * Internal mapper; kept private so DTO mapping stays in one place.
     */
    private static final class PaymentResponseMapper {

        private PaymentResponseMapper() {
        }

        static PaymentResponse toResponse(Payment payment) {
            return new PaymentResponse(
                    payment.getId(),
                    payment.getReservationId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    payment.getPaymentMethod(),
                    payment.getTransactionId(),
                    payment.getStatus(),
                    payment.getPaymentDate(),
                    payment.getCreatedAt(),
                    mask(payment.getCardLast4()));
        }

        private static String mask(String cardLast4) {
            return cardLast4 == null ? null : "**** **** **** " + cardLast4;
        }
    }

}
