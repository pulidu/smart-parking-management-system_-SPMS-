package com.smartparkingmanagementsystem.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;
import com.smartparkingmanagementsystem.parking.dto.CreateReservationRequest;
import com.smartparkingmanagementsystem.parking.repository.ParkingSpaceRepository;
import com.smartparkingmanagementsystem.parking.repository.ReservationRepository;
import com.smartparkingmanagementsystem.parking.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "eureka.client.enabled=false", "spring.config.import=",
        "spring.cloud.config.import-check.enabled=false" })
@AutoConfigureMockMvc
class ParkingServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ParkingSpaceRepository parkingSpaceRepository;

    @AfterEach
    void cleanDatabase() {
        reservationRepository.deleteAll();
        parkingSpaceRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    // ------------------------------------------------------------------
    // Parking spaces
    // ------------------------------------------------------------------

    @Test
    void createParkingSpaceReturns201() throws Exception {
        mockMvc.perform(post("/api/parking/spaces").contentType(MediaType.APPLICATION_JSON)
                        .content(spaceBody("SP-001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ownerId").value(1))
                .andExpect(jsonPath("$.spaceNumber").value("SP-001"))
                .andExpect(jsonPath("$.city").value("Colombo"))
                .andExpect(jsonPath("$.zone").value("Zone-A"))
                .andExpect(jsonPath("$.pricePerHour").value(5.5))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void createDuplicateSpaceNumberSameOwnerReturns409() throws Exception {
        mockMvc.perform(post("/api/parking/spaces").contentType(MediaType.APPLICATION_JSON)
                        .content(spaceBody("DUP-1")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/parking/spaces").contentType(MediaType.APPLICATION_JSON)
                        .content(spaceBody("DUP-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Parking space number already exists for this owner: DUP-1"));
    }

    @Test
    void createSameSpaceNumberDifferentOwnerReturns201() throws Exception {
        mockMvc.perform(post("/api/parking/spaces").contentType(MediaType.APPLICATION_JSON)
                        .content(spaceBody("OWN-1")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/parking/spaces").contentType(MediaType.APPLICATION_JSON)
                        .content(spaceBody("OWN-1", 2)))
                .andExpect(status().isCreated());
    }

    @Test
    void createMissingOwnerIdReturns400() throws Exception {
        mockMvc.perform(post("/api/parking/spaces").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spaceNumber":"SP-002","location":"Level 1","city":"Colombo","zone":"Zone-A","pricePerHour":5.50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.ownerId").exists());
    }

    @Test
    void createInvalidPriceReturns400() throws Exception {
        mockMvc.perform(post("/api/parking/spaces").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ownerId":1,"spaceNumber":"SP-003","location":"Level 1","city":"Colombo","zone":"Zone-A","pricePerHour":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pricePerHour").exists());
    }

    @Test
    void getParkingSpaceByIdReturns200() throws Exception {
        long id = createSpace("GET-1");
        mockMvc.perform(get("/api/parking/spaces/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.spaceNumber").value("GET-1"));
    }

    @Test
    void getParkingSpaceNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/parking/spaces/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Parking space not found with id: 99999"));
    }

    @Test
    void searchReturnsAllSpaces() throws Exception {
        createSpace("SRC-A", 1, "Colombo", "Zone-A");
        createSpace("SRC-B", 2, "Kandy", "Zone-B");
        mockMvc.perform(get("/api/parking/spaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void searchByCityReturnsMatchingSpaces() throws Exception {
        createSpace("SRC-C1", 1, "Colombo", "Zone-A");
        createSpace("SRC-C2", 2, "Colombo", "Zone-B");
        createSpace("SRC-C3", 3, "Kandy", "Zone-A");
        mockMvc.perform(get("/api/parking/spaces").param("city", "Colombo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void searchByZoneReturnsMatchingSpaces() throws Exception {
        createSpace("SRC-Z1", 1, "Colombo", "Zone-A");
        createSpace("SRC-Z2", 2, "Colombo", "Zone-B");
        createSpace("SRC-Z3", 3, "Kandy", "Zone-B");
        mockMvc.perform(get("/api/parking/spaces").param("zone", "Zone-B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void searchAvailableTrueReturnsOnlyAvailable() throws Exception {
        long a = createSpace("SRC-A1", 1, "Colombo", "Zone-A");
        createSpace("SRC-A2", 2, "Kandy", "Zone-B");
        setStatus(a, "OCCUPIED");
        mockMvc.perform(get("/api/parking/spaces").param("available", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].spaceNumber").value("SRC-A2"));
    }

    @Test
    void searchCityAndAvailableCombined() throws Exception {
        long a = createSpace("SRC-AC1", 1, "Colombo", "Zone-A");
        createSpace("SRC-AC2", 2, "Colombo", "Zone-B");
        createSpace("SRC-AC3", 3, "Kandy", "Zone-B");
        setStatus(a, "OCCUPIED");
        mockMvc.perform(get("/api/parking/spaces").param("city", "Colombo").param("available", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].spaceNumber").value("SRC-AC2"));
    }

    @Test
    void searchAvailableFalseReturnsNonAvailable() throws Exception {
        long a = createSpace("SRC-NA1", 1, "Colombo", "Zone-A");
        createSpace("SRC-NA2", 2, "Kandy", "Zone-B");
        setStatus(a, "OCCUPIED");
        mockMvc.perform(get("/api/parking/spaces").param("available", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].spaceNumber").value("SRC-NA1"));
    }

    @Test
    void searchCityWithoutMatchesReturnsEmpty() throws Exception {
        createSpace("SRC-EM1", 1, "Colombo", "Zone-A");
        mockMvc.perform(get("/api/parking/spaces").param("city", "Galle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateParkingSpaceReturns200() throws Exception {
        long id = createSpace("UPD-1");
        String json = """
                {"spaceNumber":"UPD-1","location":"Level 2","city":"Kandy","zone":"Zone-B","pricePerHour":7.25}
                """;
        mockMvc.perform(put("/api/parking/spaces/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Level 2"))
                .andExpect(jsonPath("$.city").value("Kandy"))
                .andExpect(jsonPath("$.zone").value("Zone-B"))
                .andExpect(jsonPath("$.pricePerHour").value(7.25))
                .andExpect(jsonPath("$.ownerId").value(1));
    }

    @Test
    void updateToTakenSpaceNumberReturns409() throws Exception {
        long first = createSpace("TAKE-1");
        createSpace("TAKE-2", 1);
        String json = """
                {"spaceNumber":"TAKE-2","location":"Level 1","city":"Colombo","zone":"Zone-A","pricePerHour":5.50}
                """;
        mockMvc.perform(put("/api/parking/spaces/{id}", first).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateParkingSpaceNotFoundReturns404() throws Exception {
        String json = """
                {"spaceNumber":"NOPE","location":"Level 1","city":"Colombo","zone":"Zone-A","pricePerHour":5.50}
                """;
        mockMvc.perform(put("/api/parking/spaces/99999").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteParkingSpaceReturns204() throws Exception {
        long id = createSpace("DEL-1");
        mockMvc.perform(delete("/api/parking/spaces/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/parking/spaces/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteParkingSpaceNotFoundReturns404() throws Exception {
        mockMvc.perform(delete("/api/parking/spaces/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatusReturns200() throws Exception {
        long id = createSpace("STS-1");
        mockMvc.perform(put("/api/parking/spaces/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"OCCUPIED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
        mockMvc.perform(get("/api/parking/spaces/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OCCUPIED"));
    }

    @Test
    void updateStatusInvalidValueReturns400() throws Exception {
        long id = createSpace("STS-2");
        mockMvc.perform(put("/api/parking/spaces/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"GARAGE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatusMissingStatusReturns400() throws Exception {
        long id = createSpace("STS-3");
        mockMvc.perform(put("/api/parking/spaces/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    // ------------------------------------------------------------------
    // Reservations
    // ------------------------------------------------------------------

    @Test
    void createReservationReturns201AndSpaceBecomesReserved() throws Exception {
        long spaceId = createSpace("RES-1");
        mockMvc.perform(post("/api/parking/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(spaceId, 1, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.vehicleId").value(1))
                .andExpect(jsonPath("$.parkingSpaceId").value(spaceId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists());
        mockMvc.perform(get("/api/parking/spaces/{id}", spaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    void createReservationSpaceNotFoundReturns404() throws Exception {
        mockMvc.perform(post("/api/parking/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(99999, 1, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Parking space not found with id: 99999"));
    }

    @Test
    void createReservationSpaceNotAvailableReturns409() throws Exception {
        long spaceId = createSpace("RES-NA");
        setStatus(spaceId, "MAINTENANCE");
        mockMvc.perform(post("/api/parking/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(spaceId, 1, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Parking space " + spaceId + " is not available for reservation (status: MAINTENANCE)"));
    }

    @Test
    void createReservationMissingUserIdReturns400() throws Exception {
        long spaceId = createSpace("RES-MU");
        mockMvc.perform(post("/api/parking/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleId":1,"parkingSpaceId":%d,"startTime":"2026-08-20T10:00:00Z","endTime":"2026-08-20T12:00:00Z"}
                                """.formatted(spaceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userId").exists());
    }

    @Test
    void createReservationMissingVehicleIdReturns400() throws Exception {
        long spaceId = createSpace("RES-MV");
        mockMvc.perform(post("/api/parking/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"parkingSpaceId":%d,"startTime":"2026-08-20T10:00:00Z","endTime":"2026-08-20T12:00:00Z"}
                                """.formatted(spaceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.vehicleId").exists());
    }

    @Test
    void createReservationStartAfterEndReturns400() throws Exception {
        long spaceId = createSpace("RES-TI");
        mockMvc.perform(post("/api/parking/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"vehicleId":1,"parkingSpaceId":%d,"startTime":"2026-08-20T12:00:00Z","endTime":"2026-08-20T10:00:00Z"}
                                """.formatted(spaceId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startTime must be before endTime"));
    }

    @Test
    void getReservationReturns200() throws Exception {
        long spaceId = createSpace("RES-G1");
        long reservationId = createReservation(spaceId, 1, 1);
        mockMvc.perform(get("/api/parking/reservations/{id}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reservationId))
                .andExpect(jsonPath("$.parkingSpaceId").value(spaceId))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getReservationNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/parking/reservations/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Reservation not found with id: 99999"));
    }

    @Test
    void listReservationsByUserReturns200() throws Exception {
        long s1 = createSpace("RES-L1");
        long s2 = createSpace("RES-L2");
        long r1 = createReservation(s1, 9, 1);
        long r2 = createReservation(s2, 9, 2);
        mockMvc.perform(get("/api/parking/reservations/user/{userId}", 9))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == %d)]", r1).exists())
                .andExpect(jsonPath("$[?(@.id == %d)]", r2).exists());
    }

    @Test
    void listReservationsByUserEmptyReturns200() throws Exception {
        mockMvc.perform(get("/api/parking/reservations/user/{userId}", 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cancelReservationReturns200AndFreesSpace() throws Exception {
        long spaceId = createSpace("RES-C1");
        long reservationId = createReservation(spaceId, 1, 1);
        mockMvc.perform(post("/api/parking/reservations/{id}/cancel", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/parking/spaces/{id}", spaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void cancelAlreadyCancelledReturns409() throws Exception {
        long spaceId = createSpace("RES-C2");
        long reservationId = createReservation(spaceId, 1, 1);
        mockMvc.perform(post("/api/parking/reservations/{id}/cancel", reservationId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/parking/reservations/{id}/cancel", reservationId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Reservation " + reservationId + " cannot be cancel: status is CANCELLED"));
    }

    @Test
    void releaseReservationReturns200AndFreesSpace() throws Exception {
        long spaceId = createSpace("RES-R1");
        long reservationId = createReservation(spaceId, 1, 1);
        mockMvc.perform(post("/api/parking/reservations/{id}/release", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(get("/api/parking/spaces/{id}", spaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void releaseWhenNotActiveReturns409() throws Exception {
        long spaceId = createSpace("RES-R2");
        long reservationId = createReservation(spaceId, 1, 1);
        mockMvc.perform(post("/api/parking/reservations/{id}/release", reservationId)).andExpect(status().isOk());
        mockMvc.perform(post("/api/parking/reservations/{id}/release", reservationId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Reservation " + reservationId + " cannot be release: status is COMPLETED"));
    }

    @Test
    void doubleReservationOnSameSpaceReturns409() throws Exception {
        long spaceId = createSpace("RES-D1");
        createReservation(spaceId, 1, 1);
        mockMvc.perform(post("/api/parking/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content(reservationBody(spaceId, 2, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void reserveCancelReserveCycleReturns201() throws Exception {
        long spaceId = createSpace("RES-CYC");
        long r1 = createReservation(spaceId, 1, 1);
        mockMvc.perform(post("/api/parking/reservations/{id}/cancel", r1)).andExpect(status().isOk());
        long r2 = createReservation(spaceId, 2, 2);
        mockMvc.perform(get("/api/parking/reservations/{id}", r2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void cancelReservationNotFoundReturns404() throws Exception {
        mockMvc.perform(post("/api/parking/reservations/99999/cancel"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Concurrency / double-reservation guard
    // ------------------------------------------------------------------

    @Test
    void concurrentReservationAttemptsOnlyOneSucceeds() throws Exception {
        long spaceId = createSpace("CONC-1");

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    reservationService.create(new CreateReservationRequest(
                            1L, 1L, spaceId,
                            Instant.parse("2026-08-20T10:00:00Z"),
                            Instant.parse("2026-08-20T12:00:00Z")));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        ready.await();
        start.countDown();

        long successCount = 0;
        for (Future<Boolean> future : results) {
            if (Boolean.TRUE.equals(future.get(15, TimeUnit.SECONDS))) {
                successCount++;
            }
        }
        executor.shutdown();

        assertEquals(1, successCount, "exactly one concurrent reservation must succeed");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long createSpace(String number) throws Exception {
        return createSpace(number, 1);
    }

    private long createSpace(String number, long ownerId) throws Exception {
        return createSpace(number, ownerId, "Colombo", "Zone-A");
    }

    private long createSpace(String number, long ownerId, String city, String zone) throws Exception {
        String body = """
                {"ownerId":%d,"spaceNumber":"%s","location":"Level 1","city":"%s","zone":"%s","pricePerHour":5.50}
                """.formatted(ownerId, number, city, zone);
        String response = mockMvc.perform(post("/api/parking/spaces")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.parse(response).read("$.id", Long.class);
    }

    private long createReservation(long spaceId, long userId, long vehicleId) throws Exception {
        String body = reservationBody(spaceId, userId, vehicleId);
        String response = mockMvc.perform(post("/api/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.parse(response).read("$.id", Long.class);
    }

    private void setStatus(long spaceId, String status) throws Exception {
        mockMvc.perform(put("/api/parking/spaces/{id}/status", spaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}
                                """.formatted(status)))
                .andExpect(status().isOk());
    }

    private static String spaceBody(String number) {
        return spaceBody(number, 1);
    }

    private static String spaceBody(String number, long ownerId) {
        return """
                {"ownerId":%d,"spaceNumber":"%s","location":"Level 1","city":"Colombo","zone":"Zone-A","pricePerHour":5.50}
                """.formatted(ownerId, number);
    }

    private static String reservationBody(long spaceId, long userId, long vehicleId) {
        return """
                {"userId":%d,"vehicleId":%d,"parkingSpaceId":%d,"startTime":"2026-08-20T10:00:00Z","endTime":"2026-08-20T12:00:00Z"}
                """.formatted(userId, vehicleId, spaceId);
    }

}
