package com.smartparkingmanagementsystem.parking.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Translates exceptions raised by the parking-service into consistent JSON error
 * responses.
 *
 * <ul>
 *   <li>parking space / reservation not found          -&gt; 404</li>
 *   <li>referenced user / vehicle not found             -&gt; 404</li>
 *   <li>duplicate space number / duplicate reservation  -&gt; 409</li>
 *   <li>invalid state transition (space or reservation) -&gt; 409</li>
 *   <li>vehicle does not belong to the requesting user  -&gt; 409</li>
 *   <li>invalid reservation (start &gt;= end)             -&gt; 400</li>
 *   <li>upstream service (User / Vehicle) unreachable   -&gt; 503</li>
 *   <li>invalid input                                    -&gt; 400</li>
 *   <li>unexpected errors                                -&gt; 500</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ParkingSpaceNotFoundException.class)
    public ResponseEntity<ApiError> handleSpaceNotFound(ParkingSpaceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiError> handleReservationNotFound(ReservationNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ReferencedResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleReferencedNotFound(ReferencedResourceNotFoundException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(VehicleOwnershipException.class)
    public ResponseEntity<ApiError> handleVehicleOwnership(VehicleOwnershipException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(UpstreamServiceUnavailableException.class)
    public ResponseEntity<ApiError> handleUpstreamUnavailable(UpstreamServiceUnavailableException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateParkingSpaceException.class)
    public ResponseEntity<ApiError> handleDuplicateSpace(DuplicateParkingSpaceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ParkingSpaceStateException.class)
    public ResponseEntity<ApiError> handleSpaceState(ParkingSpaceStateException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DuplicateReservationException.class)
    public ResponseEntity<ApiError> handleDuplicateReservation(DuplicateReservationException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ReservationStateException.class)
    public ResponseEntity<ApiError> handleReservationState(ReservationStateException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(InvalidReservationException.class)
    public ResponseEntity<ApiError> handleInvalidReservation(InvalidReservationException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        String message = "Malformed request body: " + ex.getMostSpecificCause().getMessage();
        return build(HttpStatus.BAD_REQUEST, message, request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid parameter: " + ex.getName(), request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "Resource conflicts with an existing record", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                           Map<String, String> fieldErrors) {
        ApiError body = fieldErrors == null
                ? ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI())
                : ApiError.withFieldErrors(status.value(), status.getReasonPhrase(), message,
                        request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }

}
