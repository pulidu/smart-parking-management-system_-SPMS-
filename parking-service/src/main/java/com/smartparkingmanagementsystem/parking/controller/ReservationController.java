package com.smartparkingmanagementsystem.parking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartparkingmanagementsystem.parking.dto.CreateReservationRequest;
import com.smartparkingmanagementsystem.parking.dto.ReservationResponse;
import com.smartparkingmanagementsystem.parking.service.ReservationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/parking/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse created = reservationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ReservationResponse getById(@PathVariable Long id) {
        return reservationService.getById(id);
    }

    @GetMapping("/user/{userId}")
    public List<ReservationResponse> listByUser(@PathVariable Long userId) {
        return reservationService.listByUser(userId);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id) {
        return reservationService.cancel(id);
    }

    @PostMapping("/{id}/release")
    public ReservationResponse release(@PathVariable Long id) {
        return reservationService.release(id);
    }

}
