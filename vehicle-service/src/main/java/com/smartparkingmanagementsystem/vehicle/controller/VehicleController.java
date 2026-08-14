package com.smartparkingmanagementsystem.vehicle.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartparkingmanagementsystem.vehicle.dto.CreateVehicleRequest;
import com.smartparkingmanagementsystem.vehicle.dto.UpdateVehicleRequest;
import com.smartparkingmanagementsystem.vehicle.dto.VehicleResponse;
import com.smartparkingmanagementsystem.vehicle.service.VehicleService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<VehicleResponse> register(@Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse created = vehicleService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public VehicleResponse getById(@PathVariable Long id) {
        return vehicleService.getById(id);
    }

    @GetMapping("/user/{userId}")
    public List<VehicleResponse> listByUser(@PathVariable Long userId) {
        return vehicleService.listByUser(userId);
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateVehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        vehicleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/entry")
    public VehicleResponse entry(@PathVariable Long id) {
        return vehicleService.entry(id);
    }

    @PostMapping("/{id}/exit")
    public VehicleResponse exit(@PathVariable Long id) {
        return vehicleService.exit(id);
    }

}
