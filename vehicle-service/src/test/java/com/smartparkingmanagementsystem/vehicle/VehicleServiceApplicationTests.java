package com.smartparkingmanagementsystem.vehicle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "eureka.client.enabled=false", "spring.config.import=",
        "spring.cloud.config.import-check.enabled=false" })
@AutoConfigureMockMvc
class VehicleServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void registerVehicleReturns201() throws Exception {
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(registerBody("ABC-1234")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.vehicleNumber").value("ABC-1234"))
                .andExpect(jsonPath("$.vehicleType").value("CAR"))
                .andExpect(jsonPath("$.status").value("OUTSIDE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void registerDuplicateVehicleNumberReturns409() throws Exception {
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(registerBody("DUP-111")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(registerBody("DUP-111")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Vehicle number already registered: DUP-111"));
    }

    @Test
    void registerMissingUserIdReturns400() throws Exception {
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"vehicleNumber":"A-123","vehicleType":"CAR","brand":"Toyota","model":"Corolla"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.userId").exists());
    }

    @Test
    void registerMissingBrandReturns400() throws Exception {
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"vehicleNumber":"A-123","vehicleType":"CAR","model":"Corolla"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.brand").exists());
    }

    @Test
    void registerInvalidVehicleNumberReturns400() throws Exception {
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"vehicleNumber":"!?","vehicleType":"CAR","brand":"Toyota","model":"Corolla"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.vehicleNumber").exists());
    }

    @Test
    void registerInvalidVehicleTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"vehicleNumber":"A-123","vehicleType":"SPACESHIP","brand":"Toyota","model":"Corolla"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getVehicleByIdReturns200() throws Exception {
        long id = register("GET-0001");
        mockMvc.perform(get("/api/vehicles/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.vehicleNumber").value("GET-0001"));
    }

    @Test
    void getVehicleByIdNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/vehicles/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Vehicle not found with id: 99999"));
    }

    @Test
    void listVehiclesByUserReturns200() throws Exception {
        long a = register("LST-0001", 7);
        long b = register("LST-0002", 7);
        mockMvc.perform(get("/api/vehicles/user/{userId}", 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == %d)]", a).exists())
                .andExpect(jsonPath("$[?(@.id == %d)]", b).exists());
    }

    @Test
    void listVehiclesByUserEmptyReturns200() throws Exception {
        mockMvc.perform(get("/api/vehicles/user/{userId}", 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updateVehicleReturns200() throws Exception {
        long id = register("UPD-0001");
        String json = """
                {"vehicleNumber":"UPD-0001","vehicleType":"SUV","brand":"Honda","model":"CR-V"}
                """;
        mockMvc.perform(put("/api/vehicles/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleType").value("SUV"))
                .andExpect(jsonPath("$.brand").value("Honda"))
                .andExpect(jsonPath("$.model").value("CR-V"))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void updateVehicleToTakenNumberReturns409() throws Exception {
        long first = register("TAKE-0001");
        register("TAKE-0002");
        String json = """
                {"vehicleNumber":"TAKE-0002","vehicleType":"CAR","brand":"Toyota","model":"Corolla"}
                """;
        mockMvc.perform(put("/api/vehicles/{id}", first).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateVehicleNotFoundReturns404() throws Exception {
        String json = """
                {"vehicleNumber":"NOPE-1","vehicleType":"CAR","brand":"Toyota","model":"Corolla"}
                """;
        mockMvc.perform(put("/api/vehicles/99999").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteVehicleReturns204() throws Exception {
        long id = register("DEL-0001");
        mockMvc.perform(delete("/api/vehicles/{id}", id))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/vehicles/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteVehicleNotFoundReturns404() throws Exception {
        mockMvc.perform(delete("/api/vehicles/99999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void entryMarksVehicleInsideAndRecordsEntryTime() throws Exception {
        long id = register("ENT-0001");
        mockMvc.perform(post("/api/vehicles/{id}/entry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSIDE"))
                .andExpect(jsonPath("$.entryTime").exists())
                .andExpect(jsonPath("$.exitTime").isEmpty());
    }

    @Test
    void entryWhenAlreadyInsideReturns409() throws Exception {
        long id = register("ENT-0002");
        mockMvc.perform(post("/api/vehicles/{id}/entry", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/vehicles/{id}/entry", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Vehicle is already inside: ENT-0002"));
    }

    @Test
    void exitMarksVehicleOutsideAndRecordsExitTime() throws Exception {
        long id = register("EXT-0001");
        mockMvc.perform(post("/api/vehicles/{id}/entry", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/vehicles/{id}/exit", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTSIDE"))
                .andExpect(jsonPath("$.exitTime").exists())
                .andExpect(jsonPath("$.entryTime").isEmpty());
    }

    @Test
    void exitWhenOutsideReturns409() throws Exception {
        long id = register("EXT-0002");
        mockMvc.perform(post("/api/vehicles/{id}/exit", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vehicle is not inside: EXT-0002"));
    }

    @Test
    void entryExitFullCycleReturns200() throws Exception {
        long id = register("CYC-0001");
        mockMvc.perform(post("/api/vehicles/{id}/entry", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/vehicles/{id}/exit", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/vehicles/{id}/entry", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSIDE"))
                .andExpect(jsonPath("$.entryTime").exists());
    }

    @Test
    void entryOnUnknownVehicleReturns404() throws Exception {
        mockMvc.perform(post("/api/vehicles/99999/entry"))
                .andExpect(status().isNotFound());
    }

    @Test
    void exitOnUnknownVehicleReturns404() throws Exception {
        mockMvc.perform(post("/api/vehicles/99999/exit"))
                .andExpect(status().isNotFound());
    }

    private long register(String number) throws Exception {
        return register(number, 1);
    }

    private long register(String number, long userId) throws Exception {
        String body = """
                {"userId":%d,"vehicleNumber":"%s","vehicleType":"CAR","brand":"Toyota","model":"Corolla"}
                """.formatted(userId, number);
        String response = mockMvc.perform(post("/api/vehicles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.parse(response).read("$.id", Long.class);
    }

    private static String registerBody(String number) {
        return """
                {"userId":1,"vehicleNumber":"%s","vehicleType":"CAR","brand":"Toyota","model":"Corolla"}
                """.formatted(number);
    }

}
