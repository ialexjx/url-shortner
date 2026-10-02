package com.akshat.shortener.controller;

import com.akshat.shortener.exception.ProtectedLinkException;
import com.akshat.shortener.exception.ResourceNotFoundException;
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
import org.springframework.web.servlet.ModelAndView;

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
     * Redirects short code to original target destination URL, or renders
     * the Vault (if passcode protected) or Savage Tombstone (if dead/burned).
     */
    @GetMapping("/{shortCode:[a-zA-Z0-9_-]{2,32}}")
    public Object redirect(
            @PathVariable String shortCode,
            HttpServletRequest request
    ) {
        log.debug("Incoming redirect request for short code: {}", shortCode);
        String accept = request.getHeader("Accept");
        boolean isHtml = accept != null && accept.contains("text/html");

        try {
            // 1. Agar browser request hai aur link passcode protected hai, Vault view render karo
            if (isHtml && urlShortenerService.isPasscodeProtected(shortCode)) {
                ModelAndView mav = new ModelAndView("vault");
                mav.addObject("shortCode", shortCode);
                return mav;
            }

            // 2. Resolve destination & track telemetry
            String destinationUrl = urlShortenerService.resolveAndTrack(shortCode, request);

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.LOCATION, destinationUrl);
            headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.set(HttpHeaders.PRAGMA, "no-cache");
            headers.set(HttpHeaders.EXPIRES, "0");

            return new ResponseEntity<>(headers, HttpStatus.FOUND); // 302 Found
        } catch (ProtectedLinkException ple) {
            if (isHtml) {
                ModelAndView mav = new ModelAndView("vault");
                mav.addObject("shortCode", shortCode);
                return mav;
            }
            throw ple;
        } catch (ResourceNotFoundException ex) {
            if (isHtml) {
                ModelAndView mav = new ModelAndView("tombstone");
                mav.addObject("shortCode", shortCode);
                mav.addObject("reason", ex.getMessage());
                mav.setStatus(HttpStatus.NOT_FOUND);
                return mav;
            }
            throw ex;
        }
    }
}
