package com.smartparkingmanagementsystem.parking.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartparkingmanagementsystem.parking.dto.CreateParkingSpaceRequest;
import com.smartparkingmanagementsystem.parking.dto.ParkingSpaceResponse;
import com.smartparkingmanagementsystem.parking.dto.UpdateParkingSpaceRequest;
import com.smartparkingmanagementsystem.parking.dto.UpdateStatusRequest;
import com.smartparkingmanagementsystem.parking.service.ParkingSpaceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/parking/spaces")
public class ParkingSpaceController {

    private final ParkingSpaceService parkingSpaceService;

    public ParkingSpaceController(ParkingSpaceService parkingSpaceService) {
        this.parkingSpaceService = parkingSpaceService;
    }

    @PostMapping
    public ResponseEntity<ParkingSpaceResponse> create(@Valid @RequestBody CreateParkingSpaceRequest request) {
        ParkingSpaceResponse created = parkingSpaceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Search/filter endpoint. Optional query parameters:
     * {@code city}, {@code zone}, {@code available} (true/false).
     * Example: {@code GET /api/parking/spaces?city=Colombo&available=true}
     */
    @GetMapping
    public List<ParkingSpaceResponse> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String zone,
            @RequestParam(required = false) Boolean available) {
        return parkingSpaceService.search(city, zone, available);
    }

    @GetMapping("/{id}")
    public ParkingSpaceResponse getById(@PathVariable Long id) {
        return parkingSpaceService.getById(id);
    }

    @PutMapping("/{id}")
    public ParkingSpaceResponse update(@PathVariable Long id, @Valid @RequestBody UpdateParkingSpaceRequest request) {
        return parkingSpaceService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parkingSpaceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Manual / simulated IoT status update.
     * Example: {@code PUT /api/parking/spaces/1/status} with
     * {@code {"status": "OCCUPIED"}}.
     */
    @PutMapping("/{id}/status")
    public ParkingSpaceResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return parkingSpaceService.updateStatus(id, request.status());
    }

}
