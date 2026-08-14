package com.smartparkingmanagementsystem.parking.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartparkingmanagementsystem.parking.dto.CreateParkingSpaceRequest;
import com.smartparkingmanagementsystem.parking.dto.ParkingSpaceResponse;
import com.smartparkingmanagementsystem.parking.dto.UpdateParkingSpaceRequest;
import com.smartparkingmanagementsystem.parking.exception.DuplicateParkingSpaceException;
import com.smartparkingmanagementsystem.parking.exception.ParkingSpaceNotFoundException;
import com.smartparkingmanagementsystem.parking.exception.ParkingSpaceStateException;
import com.smartparkingmanagementsystem.parking.model.ParkingSpace;
import com.smartparkingmanagementsystem.parking.model.ParkingSpaceStatus;
import com.smartparkingmanagementsystem.parking.model.ReservationStatus;
import com.smartparkingmanagementsystem.parking.repository.ParkingSpaceRepository;
import com.smartparkingmanagementsystem.parking.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business logic for parking space management, search/filter and manual
 * (IoT/simulated) status updates.
 */
@Service
@RequiredArgsConstructor
public class ParkingSpaceService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final ParkingSpaceRepository parkingSpaceRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public ParkingSpaceResponse create(CreateParkingSpaceRequest request) {
        String number = normalize(request.spaceNumber());
        if (parkingSpaceRepository.existsByOwnerIdAndSpaceNumber(request.ownerId(), number)) {
            throw new DuplicateParkingSpaceException(
                    "Parking space number already exists for this owner: " + number);
        }
        ParkingSpace space = new ParkingSpace();
        space.setOwnerId(request.ownerId());
        space.setSpaceNumber(number);
        space.setLocation(request.location().trim());
        space.setCity(request.city().trim());
        space.setZone(request.zone().trim());
        space.setPricePerHour(request.pricePerHour());
        space.setStatus(ParkingSpaceStatus.AVAILABLE);
        return ParkingSpaceResponseMapper.toResponse(parkingSpaceRepository.save(space));
    }

    @Transactional(readOnly = true)
    public ParkingSpaceResponse getById(Long id) {
        return ParkingSpaceResponseMapper.toResponse(findById(id));
    }

    /**
     * Searches parking spaces with optional city / zone filters and an
     * availability filter.
     *
     * @param available {@code null} = any status; {@code true} = AVAILABLE only;
     *                  {@code false} = everything except AVAILABLE.
     */
    @Transactional(readOnly = true)
    public List<ParkingSpaceResponse> search(String city, String zone, Boolean available) {
        if (available == null) {
            return parkingSpaceRepository.search(city, zone, true, List.of()).stream()
                    .map(ParkingSpaceResponseMapper::toResponse)
                    .toList();
        }
        List<ParkingSpaceStatus> statuses = available
                ? List.of(ParkingSpaceStatus.AVAILABLE)
                : List.of(ParkingSpaceStatus.RESERVED, ParkingSpaceStatus.OCCUPIED,
                        ParkingSpaceStatus.MAINTENANCE);
        return parkingSpaceRepository.search(city, zone, false, statuses).stream()
                .map(ParkingSpaceResponseMapper::toResponse)
                .toList();
    }

    @Transactional
    public ParkingSpaceResponse update(Long id, UpdateParkingSpaceRequest request) {
        ParkingSpace space = findById(id);
        String number = normalize(request.spaceNumber());
        if (!space.getSpaceNumber().equals(number)
                && parkingSpaceRepository.existsByOwnerIdAndSpaceNumber(space.getOwnerId(), number)) {
            throw new DuplicateParkingSpaceException(
                    "Parking space number already exists for this owner: " + number);
        }
        space.setSpaceNumber(number);
        space.setLocation(request.location().trim());
        space.setCity(request.city().trim());
        space.setZone(request.zone().trim());
        space.setPricePerHour(request.pricePerHour());
        return ParkingSpaceResponseMapper.toResponse(parkingSpaceRepository.save(space));
    }

    @Transactional
    public void delete(Long id) {
        ParkingSpace space = findById(id);
        if (reservationRepository.existsByParkingSpaceIdAndStatusIn(
                id, ACTIVE_RESERVATION_STATUSES)) {
            throw new ParkingSpaceStateException(
                    "Cannot delete parking space " + id + ": it has active reservations");
        }
        parkingSpaceRepository.delete(space);
    }

    /**
     * Manual / simulated IoT status update, e.g. marking a space OCCUPIED after
     * a sensor reports a vehicle parked in it.
     */
    @Transactional
    public ParkingSpaceResponse updateStatus(Long id, ParkingSpaceStatus status) {
        ParkingSpace space = findById(id);
        space.setStatus(status);
        return ParkingSpaceResponseMapper.toResponse(parkingSpaceRepository.save(space));
    }

    private ParkingSpace findById(Long id) {
        return parkingSpaceRepository.findById(id)
                .orElseThrow(() -> new ParkingSpaceNotFoundException("Parking space not found with id: " + id));
    }

    private static String normalize(String spaceNumber) {
        return spaceNumber.trim().toUpperCase();
    }

    /**
     * Internal mapper; kept private so DTO mapping stays in one place.
     */
    private static final class ParkingSpaceResponseMapper {

        private ParkingSpaceResponseMapper() {
        }

        static ParkingSpaceResponse toResponse(ParkingSpace space) {
            return new ParkingSpaceResponse(
                    space.getId(),
                    space.getOwnerId(),
                    space.getSpaceNumber(),
                    space.getLocation(),
                    space.getCity(),
                    space.getZone(),
                    space.getPricePerHour(),
                    space.getStatus(),
                    space.getCreatedAt(),
                    space.getUpdatedAt());
        }
    }

}
