package com.akshat.shortener;

import com.akshat.shortener.dto.ShortenRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UrlShortenerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-end shorten and redirect flow")
    void testShortenAndRedirectFlow() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .originalUrl("https://github.com/akshat/scalelink")
                .build();

        // 1. Shorten the URL
        MvcResult result = mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode", notNullValue()))
                .andExpect(jsonPath("$.shortUrl", notNullValue()))
                .andExpect(jsonPath("$.originalUrl", is("https://github.com/akshat/scalelink")))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String shortCode = objectMapper.readTree(responseJson).get("shortCode").asText();

        // 2. Perform 302 Redirect
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://github.com/akshat/scalelink"));

        // 3. Check Analytics
        mockMvc.perform(get("/api/v1/analytics/" + shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode", is(shortCode)));
    }

    @Test
    @DisplayName("Custom alias creation and collision detection")
    void testCustomAliasCollision() throws Exception {
        String uniqueAlias = "portfolio-" + System.currentTimeMillis();
        ShortenRequest req1 = ShortenRequest.builder()
                .originalUrl("https://example.com/project-1")
                .customAlias(uniqueAlias)
                .build();

        // First attempt should succeed
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode", is(uniqueAlias)));

        // Duplicate attempt with same alias should fail with 409 Conflict
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("pehle se use me hai")));
    }

    @Test
    @DisplayName("Non-existent short URL returns 404 Not Found")
    void testNonExistentUrl() throws Exception {
        mockMvc.perform(get("/nonExistentKey999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Health and System Telemetry endpoints return 200 OK")
    void testHealthEndpoints() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));

        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("HEALTHY_OPTIMAL")))
                .andExpect(jsonPath("$.virtualThreadsEnabled", is(true)));
    }
}
