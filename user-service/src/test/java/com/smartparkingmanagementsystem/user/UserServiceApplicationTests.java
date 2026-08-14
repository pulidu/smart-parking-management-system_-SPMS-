package com.smartparkingmanagementsystem.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = { "eureka.client.enabled=false", "spring.config.import=",
        "spring.cloud.config.import-check.enabled=false" })
@AutoConfigureMockMvc
class UserServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void registerUserReturns201AndPersists() throws Exception {
        String json = """
                {
                  "name": "Alice Driver",
                  "email": "alice@example.com",
                  "password": "secret123",
                  "phone": "+1-555-0100",
                  "role": "DRIVER"
                }
                """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Alice Driver"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void registerDuplicateEmailReturns409() throws Exception {
        String json = """
                {
                  "name": "Bob Owner",
                  "email": "bob@example.com",
                  "password": "secret123",
                  "role": "OWNER"
                }
                """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email already registered: bob@example.com"));
    }

    @Test
    void registerDuplicateEmailMixedCaseReturns409() throws Exception {
        String first = """
                {
                  "name": "Case Sensitive",
                  "email": "case@example.com",
                  "password": "secret123",
                  "role": "DRIVER"
                }
                """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());
        String mixedCase = """
                {
                  "name": "Case Sensitive",
                  "email": "Case@Example.com",
                  "password": "secret123",
                  "role": "DRIVER"
                }
                """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(mixedCase))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Email already registered: Case@Example.com"));
    }

    @Test
    void registerInvalidEmailReturns400() throws Exception {
        String json = """
                {
                  "name": "Carol",
                  "email": "not-an-email",
                  "password": "secret123",
                  "role": "DRIVER"
                }
                """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void registerMissingNameReturns400() throws Exception {
        String json = """
                {
                  "email": "carol@example.com",
                  "password": "secret123",
                  "role": "DRIVER"
                }
                """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void registerShortPasswordReturns400() throws Exception {
        String json = """
                {
                  "name": "Carol",
                  "email": "carol@example.com",
                  "password": "123",
                  "role": "DRIVER"
                }
                """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void registerInvalidRoleReturns400() throws Exception {
        String json = """
                {
                  "name": "Carol",
                  "email": "carol@example.com",
                  "password": "secret123",
                  "role": "SUPERHERO"
                }
                """;
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginSucceedsReturns200WithProfile() throws Exception {
        register("dave@example.com");
        String json = """
                {
                  "email": "dave@example.com",
                  "password": "secret123"
                }
                """;
        mockMvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isEmpty())
                .andExpect(jsonPath("$.user.email").value("dave@example.com"))
                .andExpect(jsonPath("$.user.role").value("DRIVER"));
    }

    @Test
    void loginWrongPasswordReturns401() throws Exception {
        register("erin@example.com");
        String json = """
                {
                  "email": "erin@example.com",
                  "password": "wrong-pass"
                }
                """;
        mockMvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginUnknownEmailReturns401() throws Exception {
        String json = """
                {
                  "email": "ghost@example.com",
                  "password": "secret123"
                }
                """;
        mockMvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserByIdReturns200() throws Exception {
        long id = register("frank@example.com");
        mockMvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value("frank@example.com"));
    }

    @Test
    void getUserByIdNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/users/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found with id: 99999"));
    }

    @Test
    void updateUserReturns200() throws Exception {
        long id = register("grace@example.com");
        String json = """
                {
                  "name": "Grace Updated",
                  "email": "grace@example.com",
                  "phone": "+44-20-7946-0000"
                }
                """;
        mockMvc.perform(put("/api/users/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Grace Updated"))
                .andExpect(jsonPath("$.phone").value("+44-20-7946-0000"));
    }

    @Test
    void updateUserToTakenEmailReturns409() throws Exception {
        long first = register("henry@example.com");
        register("ivy@example.com");
        String json = """
                {
                  "name": "Henry",
                  "email": "ivy@example.com"
                }
                """;
        mockMvc.perform(put("/api/users/{id}", first).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateUserNotFoundReturns404() throws Exception {
        String json = """
                {
                  "name": "Nobody",
                  "email": "nobody@example.com"
                }
                """;
        mockMvc.perform(put("/api/users/99999").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBookingsReturns200EmptyPlaceholder() throws Exception {
        long id = register("jack@example.com");
        mockMvc.perform(get("/api/users/{id}/bookings", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
                .andExpect(jsonPath("$.bookings").isArray())
                .andExpect(jsonPath("$.bookings.length()").value(0));
    }

    @Test
    void getBookingsForUnknownUserReturns404() throws Exception {
        mockMvc.perform(get("/api/users/99999/bookings"))
                .andExpect(status().isNotFound());
    }

    private long register(String email) throws Exception {
        String json = """
                {
                  "name": "Auto Test",
                  "email": "%s",
                  "password": "secret123",
                  "phone": "+1-555-0100",
                  "role": "DRIVER"
                }
                """.formatted(email);
        String body = mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.parse(body).read("$.id", Long.class);
    }

}
