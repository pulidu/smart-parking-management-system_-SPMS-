package com.smartparkingmanagementsystem.parking.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartparkingmanagementsystem.parking.dto.CreateReservationRequest;
import com.smartparkingmanagementsystem.parking.dto.ReservationResponse;
import com.smartparkingmanagementsystem.parking.exception.DuplicateReservationException;
import com.smartparkingmanagementsystem.parking.exception.InvalidReservationException;
import com.smartparkingmanagementsystem.parking.exception.ParkingSpaceNotFoundException;
import com.smartparkingmanagementsystem.parking.exception.ParkingSpaceStateException;
import com.smartparkingmanagementsystem.parking.exception.ReservationNotFoundException;
import com.smartparkingmanagementsystem.parking.exception.ReservationStateException;
import com.smartparkingmanagementsystem.parking.model.ParkingSpace;
import com.smartparkingmanagementsystem.parking.model.ParkingSpaceStatus;
import com.smartparkingmanagementsystem.parking.model.Reservation;
import com.smartparkingmanagementsystem.parking.model.ReservationStatus;
import com.smartparkingmanagementsystem.parking.repository.ParkingSpaceRepository;
import com.smartparkingmanagementsystem.parking.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Business logic for reservations. The reservation flow is transactional and
 * serialized on the parking space row (pessimistic lock) so two concurrent
 * reservation attempts cannot both succeed on the same space.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES =
            List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservationRepository;
    private final ParkingSpaceRepository parkingSpaceRepository;

    /**
     * Creates a reservation. Business rules:
     * <ol>
     *   <li>parking space must exist (else 404)</li>
     *   <li>parking space must be AVAILABLE (else 409)</li>
     *   <li>userId and vehicleId must be provided (bean validation, 400)</li>
     *   <li>startTime must be before endTime (else 400)</li>
     *   <li>no active reservation may already exist for the space (else 409)</li>
     *   <li>reservation is created as PENDING</li>
     *   <li>space status changes AVAILABLE -&gt; RESERVED</li>
     * </ol>
     */
    @Transactional
    public ReservationResponse create(CreateReservationRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new InvalidReservationException("startTime must be before endTime");
        }

        // Lock the space row so concurrent reservation attempts serialize.
        ParkingSpace space = parkingSpaceRepository.findByIdForUpdate(request.parkingSpaceId())
                .orElseThrow(() -> new ParkingSpaceNotFoundException(
                        "Parking space not found with id: " + request.parkingSpaceId()));

        if (space.getStatus() != ParkingSpaceStatus.AVAILABLE) {
            throw new ParkingSpaceStateException(
                    "Parking space " + request.parkingSpaceId()
                            + " is not available for reservation (status: " + space.getStatus() + ")");
        }
        if (reservationRepository.existsByParkingSpaceIdAndStatusIn(
                request.parkingSpaceId(), ACTIVE_RESERVATION_STATUSES)) {
            throw new DuplicateReservationException(
                    "Parking space " + request.parkingSpaceId() + " already has an active reservation");
        }

        Reservation reservation = new Reservation();
        reservation.setUserId(request.userId());
        reservation.setVehicleId(request.vehicleId());
        reservation.setParkingSpaceId(request.parkingSpaceId());
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setStatus(ReservationStatus.PENDING);

        reservationRepository.save(reservation);
        space.setStatus(ParkingSpaceStatus.RESERVED);
        parkingSpaceRepository.save(space);

        return ReservationResponseMapper.toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getById(Long id) {
        return ReservationResponseMapper.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> listByUser(Long userId) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ReservationResponseMapper::toResponse)
                .toList();
    }

    /**
     * Cancels an active reservation and frees the parking space.
     */
    @Transactional
    public ReservationResponse cancel(Long id) {
        Reservation reservation = findByIdForUpdate(id);
        ensureActive(reservation, "cancel");
        reservation.setStatus(ReservationStatus.CANCELLED);
        freeSpace(reservation.getParkingSpaceId());
        return ReservationResponseMapper.toResponse(reservation);
    }

    /**
     * Releases an active reservation (parking finished) and frees the space.
     */
    @Transactional
    public ReservationResponse release(Long id) {
        Reservation reservation = findByIdForUpdate(id);
        ensureActive(reservation, "release");
        reservation.setStatus(ReservationStatus.COMPLETED);
        freeSpace(reservation.getParkingSpaceId());
        return ReservationResponseMapper.toResponse(reservation);
    }

    private void ensureActive(Reservation reservation, String operation) {
        if (!ACTIVE_RESERVATION_STATUSES.contains(reservation.getStatus())) {
            throw new ReservationStateException(
                    "Reservation " + reservation.getId() + " cannot be "
                            + operation + ": status is " + reservation.getStatus());
        }
    }

    private void freeSpace(Long parkingSpaceId) {
        parkingSpaceRepository.findById(parkingSpaceId).ifPresent(space -> {
            space.setStatus(ParkingSpaceStatus.AVAILABLE);
            parkingSpaceRepository.save(space);
        });
    }

    private Reservation findById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with id: " + id));
    }

    private Reservation findByIdForUpdate(Long id) {
        return reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found with id: " + id));
    }

    /**
     * Internal mapper; kept private so DTO mapping stays in one place.
     */
    private static final class ReservationResponseMapper {

        private ReservationResponseMapper() {
        }

        static ReservationResponse toResponse(Reservation reservation) {
            return new ReservationResponse(
                    reservation.getId(),
                    reservation.getUserId(),
                    reservation.getVehicleId(),
                    reservation.getParkingSpaceId(),
                    reservation.getStartTime(),
                    reservation.getEndTime(),
                    reservation.getStatus(),
                    reservation.getCreatedAt());
        }
    }

}
