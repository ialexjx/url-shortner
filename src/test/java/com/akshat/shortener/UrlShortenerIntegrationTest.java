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

import java.util.Map;

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

    @Test
    @DisplayName("Admin Overview requires valid admin key")
    void testAdminOverviewSecurity() throws Exception {
        // Without key or with invalid key -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/overview?key=wrongKey"))
                .andExpect(status().isUnauthorized());

        // With valid key -> 200 OK
        mockMvc.perform(get("/api/v1/admin/overview?key=admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUrls", notNullValue()))
                .andExpect(jsonPath("$.totalClicks", notNullValue()));
    }

    @Test
    @DisplayName("Admin URL listing and toggle status flow")
    void testAdminListAndToggleFlow() throws Exception {
        // Create a URL
        ShortenRequest request = ShortenRequest.builder()
                .originalUrl("https://news.ycombinator.com/item?id=12345")
                .customAlias("admin-test-" + System.currentTimeMillis())
                .build();

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Admin list URLs
        mockMvc.perform(get("/api/v1/admin/urls?key=admin123&search=" + request.getCustomAlias()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));

        // Admin toggle status (Deactivate)
        mockMvc.perform(post("/api/v1/admin/urls/" + request.getCustomAlias() + "/toggle?key=admin123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive", is(false)));

        // Requesting deactivated URL should return 404
        mockMvc.perform(get("/" + request.getCustomAlias()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Burn After Reading: First access redirects, second access is 404")
    void testBurnAfterReadingFlow() throws Exception {
        String code = "burn-" + System.currentTimeMillis();
        ShortenRequest request = ShortenRequest.builder()
                .originalUrl("https://github.com/ialexjx")
                .customAlias(code)
                .burnAfterReading(true)
                .build();

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.burnAfterReading", is(true)));

        // 1st access: Returns 302 Redirect
        mockMvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://github.com/ialexjx"));

        // 2nd access: Incinerated! Returns 404
        mockMvc.perform(get("/" + code))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Secret Vault: Protected link requires correct passcode")
    void testPasscodeVaultFlow() throws Exception {
        String code = "vault-" + System.currentTimeMillis();
        ShortenRequest request = ShortenRequest.builder()
                .originalUrl("https://github.com/ialexjx")
                .customAlias(code)
                .passcode("topSecret77")
                .build();

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isProtected", is(true)));

        // Browser GET returns Vault view
        mockMvc.perform(get("/" + code).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(view().name("vault"));

        // Unlock with wrong passcode -> 401 Unauthorized
        mockMvc.perform(post("/api/v1/unlock/" + code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("passcode", "wrongGuess"))))
                .andExpect(status().isUnauthorized());

        // Unlock with correct passcode -> 200 OK
        mockMvc.perform(post("/api/v1/unlock/" + code)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("passcode", "topSecret77"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationUrl", is("https://github.com/ialexjx")));
    }

    @Test
    @DisplayName("Browser GET on dead link renders savage Tombstone view")
    void testTombstoneBrowserView() throws Exception {
        mockMvc.perform(get("/nonExistentKey999").accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound())
                .andExpect(view().name("tombstone"))
                .andExpect(model().attributeExists("shortCode"))
                .andExpect(model().attributeExists("reason"));
    }
}
