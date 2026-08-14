package com.smartparkingmanagementsystem.vehicle.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartparkingmanagementsystem.vehicle.dto.CreateVehicleRequest;
import com.smartparkingmanagementsystem.vehicle.dto.UpdateVehicleRequest;
import com.smartparkingmanagementsystem.vehicle.dto.VehicleResponse;
import com.smartparkingmanagementsystem.vehicle.exception.DuplicateVehicleNumberException;
import com.smartparkingmanagementsystem.vehicle.exception.VehicleNotFoundException;
import com.smartparkingmanagementsystem.vehicle.exception.VehicleStateException;
import com.smartparkingmanagementsystem.vehicle.model.Vehicle;
import com.smartparkingmanagementsystem.vehicle.model.VehicleStatus;
import com.smartparkingmanagementsystem.vehicle.repository.VehicleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business logic for vehicle registration, management and the simulated
 * entry/exit state machine.
 */
@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    @Transactional
    public VehicleResponse register(CreateVehicleRequest request) {
        String number = normalize(request.vehicleNumber());
        if (vehicleRepository.existsByVehicleNumber(number)) {
            throw new DuplicateVehicleNumberException("Vehicle number already registered: " + number);
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setUserId(request.userId());
        vehicle.setVehicleNumber(number);
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setBrand(request.brand());
        vehicle.setModel(request.model());
        vehicle.setStatus(VehicleStatus.OUTSIDE);
        return VehicleResponseMapper.toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public VehicleResponse getById(Long id) {
        return VehicleResponseMapper.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> listByUser(Long userId) {
        return vehicleRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(VehicleResponseMapper::toResponse)
                .toList();
    }

    @Transactional
    public VehicleResponse update(Long id, UpdateVehicleRequest request) {
        Vehicle vehicle = findById(id);
        String number = normalize(request.vehicleNumber());
        if (!vehicle.getVehicleNumber().equals(number)) {
            if (vehicleRepository.existsByVehicleNumber(number)) {
                throw new DuplicateVehicleNumberException("Vehicle number already in use: " + number);
            }
            vehicle.setVehicleNumber(number);
        }
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setBrand(request.brand());
        vehicle.setModel(request.model());
        return VehicleResponseMapper.toResponse(vehicleRepository.save(vehicle));
    }

    @Transactional
    public void delete(Long id) {
        Vehicle vehicle = findById(id);
        vehicleRepository.delete(vehicle);
    }

    /**
     * Simulates the vehicle driving in: flips status to INSIDE and records the
     * entry time. Fails with 409 if the vehicle is already inside.
     */
    @Transactional
    public VehicleResponse entry(Long id) {
        Vehicle vehicle = findById(id);
        if (vehicle.getStatus() == VehicleStatus.INSIDE) {
            throw new VehicleStateException("Vehicle is already inside: " + vehicle.getVehicleNumber());
        }
        vehicle.setStatus(VehicleStatus.INSIDE);
        vehicle.setEntryTime(Instant.now());
        vehicle.setExitTime(null);
        return VehicleResponseMapper.toResponse(vehicleRepository.save(vehicle));
    }

    /**
     * Simulates the vehicle driving out: flips status to OUTSIDE and records the
     * exit time. Fails with 409 if the vehicle is not currently inside.
     */
    @Transactional
    public VehicleResponse exit(Long id) {
        Vehicle vehicle = findById(id);
        if (vehicle.getStatus() == VehicleStatus.OUTSIDE) {
            throw new VehicleStateException("Vehicle is not inside: " + vehicle.getVehicleNumber());
        }
        vehicle.setStatus(VehicleStatus.OUTSIDE);
        vehicle.setExitTime(Instant.now());
        vehicle.setEntryTime(null);
        return VehicleResponseMapper.toResponse(vehicleRepository.save(vehicle));
    }

    private Vehicle findById(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new VehicleNotFoundException("Vehicle not found with id: " + id));
    }

    private static String normalize(String vehicleNumber) {
        return vehicleNumber.trim().toUpperCase();
    }

    /**
     * Internal mapper; kept private so DTO mapping stays in one place.
     */
    private static final class VehicleResponseMapper {

        private VehicleResponseMapper() {
        }

        static VehicleResponse toResponse(Vehicle vehicle) {
            return new VehicleResponse(
                    vehicle.getId(),
                    vehicle.getUserId(),
                    vehicle.getVehicleNumber(),
                    vehicle.getVehicleType(),
                    vehicle.getBrand(),
                    vehicle.getModel(),
                    vehicle.getStatus(),
                    vehicle.getEntryTime(),
                    vehicle.getExitTime(),
                    vehicle.getCreatedAt(),
                    vehicle.getUpdatedAt());
        }
    }

}
