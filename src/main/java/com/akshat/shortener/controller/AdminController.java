package com.akshat.shortener.controller;

import com.akshat.shortener.dto.AdminOverviewResponse;
import com.akshat.shortener.dto.AdminUrlItemResponse;
import com.akshat.shortener.dto.ErrorResponse;
import com.akshat.shortener.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ==============================================================================
 * Admin Portal Controller (Web UI + REST Endpoints)
 * ==============================================================================
 * 
 * Endpoints:
 * - GET  /admin                              : Renders the Admin Web Dashboard
 * - GET  /api/v1/admin/overview              : Overall metrics (Total URLs, clicks, active/expired)
 * - GET  /api/v1/admin/urls                  : Paginated & searchable table of all links
 * - POST /api/v1/admin/urls/{code}/toggle    : Activate / Deactivate URL
 * - DELETE /api/v1/admin/urls/{code}         : Permanent link deletion
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService adminService;

    @Value("${scalelink.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Serves the Admin Portal Dashboard Template
     */
    @GetMapping("/admin")
    public String adminPage(Model model) {
        model.addAttribute("baseUrl", baseUrl);
        return "admin";
    }

    /**
     * Admin Overview Metrics REST API
     */
    @GetMapping("/api/v1/admin/overview")
    @ResponseBody
    public ResponseEntity<?> getAdminOverview(
            @RequestHeader(value = "X-Admin-Key", required = false) String headerKey,
            @RequestParam(value = "key", required = false) String paramKey,
            HttpServletRequest request
    ) {
        String key = headerKey != null ? headerKey : paramKey;
        if (!adminService.isValidAdminKey(key)) {
            return buildUnauthorized(request.getRequestURI());
        }

        AdminOverviewResponse overview = adminService.getOverview();
        return ResponseEntity.ok(overview);
    }

    /**
     * Paginated Links Table with Search Filter
     */
    @GetMapping("/api/v1/admin/urls")
    @ResponseBody
    public ResponseEntity<?> listUrls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestHeader(value = "X-Admin-Key", required = false) String headerKey,
            @RequestParam(value = "key", required = false) String paramKey,
            HttpServletRequest request
    ) {
        String key = headerKey != null ? headerKey : paramKey;
        if (!adminService.isValidAdminKey(key)) {
            return buildUnauthorized(request.getRequestURI());
        }

        Page<AdminUrlItemResponse> urlPage = adminService.listUrls(search, page, size);
        return ResponseEntity.ok(urlPage);
    }

    /**
     * Toggle Link Active Status (Activate / Deactivate)
     */
    @PostMapping("/api/v1/admin/urls/{shortCode}/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleUrlStatus(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-Admin-Key", required = false) String headerKey,
            @RequestParam(value = "key", required = false) String paramKey,
            HttpServletRequest request
    ) {
        String key = headerKey != null ? headerKey : paramKey;
        if (!adminService.isValidAdminKey(key)) {
            return buildUnauthorized(request.getRequestURI());
        }

        boolean active = adminService.toggleUrlStatus(shortCode);
        return ResponseEntity.ok(Map.of(
                "shortCode", shortCode,
                "isActive", active,
                "message", active ? "Link successfully reactivated" : "Link successfully deactivated"
        ));
    }

    /**
     * Delete Short URL Permanently
     */
    @DeleteMapping("/api/v1/admin/urls/{shortCode}")
    @ResponseBody
    public ResponseEntity<?> deleteUrl(
            @PathVariable String shortCode,
            @RequestHeader(value = "X-Admin-Key", required = false) String headerKey,
            @RequestParam(value = "key", required = false) String paramKey,
            HttpServletRequest request
    ) {
        String key = headerKey != null ? headerKey : paramKey;
        if (!adminService.isValidAdminKey(key)) {
            return buildUnauthorized(request.getRequestURI());
        }

        adminService.deleteUrl(shortCode);
        return ResponseEntity.ok(Map.of(
                "shortCode", shortCode,
                "status", "DELETED",
                "message", "Short URL successfully deleted from DB & Cache"
        ));
    }

    private ResponseEntity<ErrorResponse> buildUnauthorized(String path) {
        ErrorResponse err = ErrorResponse.builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message("Invalid or missing Admin Key. (Portfolio Demo Key: admin123)")
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
        return new ResponseEntity<>(err, HttpStatus.UNAUTHORIZED);
    }
}
