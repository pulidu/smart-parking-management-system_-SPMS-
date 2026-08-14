package com.smartparkingmanagementsystem.payment;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.smartparkingmanagementsystem.payment.exception.ReservationNotFoundException;
import com.smartparkingmanagementsystem.payment.repository.PaymentRepository;
import com.smartparkingmanagementsystem.payment.service.ReservationVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "eureka.client.enabled=false", "spring.config.import=",
        "spring.cloud.config.import-check.enabled=false" })
@AutoConfigureMockMvc
class PaymentServiceApplicationTests {

    private static final String VALID_CARD = "4111111111111111";
    private static final String MOCK_FAILED_CARD = "4000000000000002";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private ReservationVerifier reservationVerifier;

    @AfterEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    // ------------------------------------------------------------------
    // Process a payment
    // ------------------------------------------------------------------

    @Test
    void createCardPaymentReturns201SuccessWithMaskedCard() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(1, 1, 500, VALID_CARD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.reservationId").value(1))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.amount").value(500.0))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentDate").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.maskedCardNumber").value("**** **** **** 1111"));
    }

    @Test
    void createCashPaymentReturns201Success() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":2,"userId":1,"amount":300.50,"paymentMethod":"CASH"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.maskedCardNumber").doesNotExist());
    }

    @Test
    void createMockWalletPaymentReturns201Success() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":3,"userId":1,"amount":75,"paymentMethod":"MOCK_WALLET"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentMethod").value("MOCK_WALLET"));
    }

    @Test
    void createPaymentWithMockFailedCardReturns201Failed() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(4, 1, 500, MOCK_FAILED_CARD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());
    }

    @Test
    void createCardPaymentWithoutCardNumberReturns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":5,"userId":1,"amount":500,"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("cardNumber is required for CARD payments"));
    }

    @Test
    void createPaymentInvalidCardFormatReturns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(6, 1, 500, "1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cardNumber").exists());
    }

    @Test
    void createPaymentNonNumericCardReturns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(7, 1, 500, "4111-1111-1111-1111")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cardNumber").exists());
    }

    @Test
    void createPaymentCardFailingLuhnReturns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(8, 1, 500, "1111111111111111")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("cardNumber failed the Luhn check"));
    }

    @Test
    void createPaymentMissingAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":9,"userId":1,"paymentMethod":"CASH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void createPaymentZeroAmountReturns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":10,"userId":1,"amount":0,"paymentMethod":"CASH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void createPaymentInvalidPaymentMethodReturns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":11,"userId":1,"amount":500,"paymentMethod":"BITCOIN"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPaymentMissingUserIdReturns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reservationId":12,"amount":500,"paymentMethod":"CASH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userId").exists());
    }

    @Test
    void createPaymentMissingReservationIdReturns400() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"amount":500,"paymentMethod":"CASH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reservationId").exists());
    }

    @Test
    void createPaymentReservationNotFoundReturns404() throws Exception {
        doThrow(new ReservationNotFoundException("Reservation not found with id: 99999"))
                .when(reservationVerifier).verifyExists(99999L);
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(99999, 1, 500, VALID_CARD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Reservation not found with id: 99999"));
    }

    @Test
    void createDuplicatePaymentReturns409() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(100, 1, 500, VALID_CARD)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(100, 1, 500, VALID_CARD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("A payment already exists for reservation 100"));
    }

    @Test
    void retryAfterFailedPaymentIsAllowedReturns201() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(101, 1, 500, MOCK_FAILED_CARD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(101, 1, 500, VALID_CARD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void differentReservationsCanEachBePaidReturns201() throws Exception {
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(200, 1, 500, VALID_CARD)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody(201, 1, 500, VALID_CARD)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Retrieve payments
    // ------------------------------------------------------------------

    @Test
    void getPaymentReturns200() throws Exception {
        long id = createPayment(cardBody(300, 7, 250.75, VALID_CARD));
        mockMvc.perform(get("/api/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.reservationId").value(300))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.amount").value(250.75))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.maskedCardNumber").value("**** **** **** 1111"));
    }

    @Test
    void getPaymentNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/payments/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Payment not found with id: 99999"));
    }

    @Test
    void listPaymentsByReservationReturns200() throws Exception {
        long failed = createPayment(cardBody(400, 1, 500, MOCK_FAILED_CARD));
        long success = createPayment(cardBody(400, 1, 500, VALID_CARD));
        mockMvc.perform(get("/api/payments/reservation/{reservationId}", 400))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == %d)]", failed).exists())
                .andExpect(jsonPath("$[?(@.id == %d)]", success).exists());
    }

    @Test
    void listPaymentsByReservationEmptyReturns200() throws Exception {
        mockMvc.perform(get("/api/payments/reservation/{reservationId}", 999))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listPaymentsByUserReturns200() throws Exception {
        long p1 = createPayment(cardBody(500, 42, 500, VALID_CARD));
        long p2 = createPayment(cardBody(501, 42, 200, VALID_CARD));
        mockMvc.perform(get("/api/payments/user/{userId}", 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == %d)]", p1).exists())
                .andExpect(jsonPath("$[?(@.id == %d)]", p2).exists());
    }

    @Test
    void listPaymentsByUserEmptyReturns200() throws Exception {
        mockMvc.perform(get("/api/payments/user/{userId}", 999))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------
    // Receipts
    // ------------------------------------------------------------------

    @Test
    void getReceiptReturns200() throws Exception {
        long id = createPayment(cardBody(600, 3, 120.40, VALID_CARD));
        String transactionId = JsonPath.parse(getPaymentJson(id)).read("$.transactionId");
        mockMvc.perform(get("/api/payments/{id}/receipt", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value("RCPT-" + id))
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.reservationId").value(600))
                .andExpect(jsonPath("$.userId").value(3))
                .andExpect(jsonPath("$.amount").value(120.40))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.paymentDate").exists());
    }

    @Test
    void getReceiptNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/payments/99999/receipt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void generatedTransactionIdsAreUniqueAcrossPayments() throws Exception {
        long p1 = createPayment(cardBody(700, 1, 500, VALID_CARD));
        long p2 = createPayment(cardBody(701, 1, 500, VALID_CARD));
        String t1 = JsonPath.parse(getPaymentJson(p1)).read("$.transactionId");
        String t2 = JsonPath.parse(getPaymentJson(p2)).read("$.transactionId");
        assertNotEquals(t1, t2);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long createPayment(String body) throws Exception {
        String response = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.parse(response).read("$.id", Long.class);
    }

    private String getPaymentJson(long id) throws Exception {
        return mockMvc.perform(get("/api/payments/{id}", id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String cardBody(long reservationId, long userId, Number amount, String cardNumber) {
        return """
                {"reservationId":%d,"userId":%d,"amount":%s,"paymentMethod":"CARD","cardNumber":"%s"}
                """.formatted(reservationId, userId, amount, cardNumber);
    }

}
