package com.smartparkingmanagementsystem.payment.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartparkingmanagementsystem.payment.dto.CreatePaymentRequest;
import com.smartparkingmanagementsystem.payment.dto.PaymentResponse;
import com.smartparkingmanagementsystem.payment.dto.ReceiptResponse;
import com.smartparkingmanagementsystem.payment.service.PaymentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Processes a mock payment. The transaction is always stored (even when the
     * mock gateway fails) so the response is {@code 201 Created} with the status
     * field ({@code SUCCESS} or {@code FAILED}) describing the outcome.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse created = paymentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public PaymentResponse getById(@PathVariable Long id) {
        return paymentService.getById(id);
    }

    @GetMapping("/reservation/{reservationId}")
    public List<PaymentResponse> listByReservation(@PathVariable Long reservationId) {
        return paymentService.listByReservation(reservationId);
    }

    @GetMapping("/user/{userId}")
    public List<PaymentResponse> listByUser(@PathVariable Long userId) {
        return paymentService.listByUser(userId);
    }

    @GetMapping("/{id}/receipt")
    public ReceiptResponse getReceipt(@PathVariable Long id) {
        return paymentService.getReceipt(id);
    }

}
