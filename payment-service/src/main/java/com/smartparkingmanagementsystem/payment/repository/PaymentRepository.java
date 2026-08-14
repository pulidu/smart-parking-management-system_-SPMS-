package com.smartparkingmanagementsystem.payment.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartparkingmanagementsystem.payment.model.Payment;
import com.smartparkingmanagementsystem.payment.model.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByReservationIdOrderByCreatedAtDesc(Long reservationId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByReservationIdAndStatusIn(Long reservationId, Collection<PaymentStatus> statuses);

}
