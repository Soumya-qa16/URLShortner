package com.example.controller;
import com.example.dto.AnalyticsResponse;
import com.example.dto.ShortenRequest;
import com.example.service.AnalyticsService;
import com.example.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
public class UrlController {

    private final UrlService urlService;
    private final AnalyticsService analyticsService;

    @Value("${app.base-url}")
    private String baseUrl;

    // Explicit constructor injection replaces @RequiredArgsConstructor
    public UrlController(UrlService urlService, AnalyticsService analyticsService) {
        this.urlService = urlService;
        this.analyticsService = analyticsService;
    }

    @PostMapping("/api/v1/shorten")
    public ResponseEntity<Map<String, String>> shortenUrl(@Valid @RequestBody ShortenRequest request) {
        String shortKey = urlService.shortenUrl(request);
        return ResponseEntity.ok(Map.of(
                "shortKey", shortKey,
                "shortUrl", baseUrl + shortKey
        ));
    }

    @GetMapping("/{shortKey}")
    public ResponseEntity<Void> redirectToLongUrl(
            @PathVariable String shortKey,
            HttpServletRequest request) {

        return urlService.getLongUrl(shortKey).map(longUrl -> {
            analyticsService.logClickAsync(
                    shortKey,
                    request.getRemoteAddr(),
                    request.getHeader(HttpHeaders.USER_AGENT)
            );

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(longUrl))
                    .<Void>build();
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/v1/analytics/{shortKey}")
    public ResponseEntity<AnalyticsResponse> getAnalytics(@PathVariable String shortKey) {
        return ResponseEntity.ok(urlService.getAnalytics(shortKey));
    }
}