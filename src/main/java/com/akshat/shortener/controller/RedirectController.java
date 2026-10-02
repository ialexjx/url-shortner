package com.akshat.shortener.controller;

import com.akshat.shortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * ==============================================================================
 * High-Speed URL Redirection Controller
 * ==============================================================================
 * 
 * System Design Interview Tradeoff (301 Permanent vs 302 Temporary Redirect):
 * 
 * Kyun 302 Found use kiya 301 ke bajaye?
 * 1. 301 Moved Permanently: Browser destination URL ko locally cache kar leta hai.
 *    Agle click par browser hamare server ko request hi nahi bhejega!
 *    Result: Analytics aur Click Count tracking totally fail ho jayegi.
 * 
 * 2. 302 Found (Temporary): Browser har baar hamare server par aata hai.
 *    Hamari dual-caching (L1 Caffeine < 0.1ms) se response instant milta hai,
 *    aur hum 100% clicks aur telemetry asynchronously track kar pate hain.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RedirectController {

    private final UrlShortenerService urlShortenerService;

    /**
     * Redirects short code to original target destination URL
     */
    @GetMapping("/{shortCode:[a-zA-Z0-9_-]{2,32}}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request
    ) {
        log.debug("Incoming redirect request for short code: {}", shortCode);
        String destinationUrl = urlShortenerService.resolveAndTrack(shortCode, request);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LOCATION, destinationUrl);
        // Browser ko cache karne se mana karte hain taaki future clicks track ho sakein
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.EXPIRES, "0");

        return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302 Found
    }
}
