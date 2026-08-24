package com.example.service;
import com.example.dto.AnalyticsResponse;
import com.example.dto.ShortenRequest;
import com.example.entity.UrlClick;
import com.example.entity.UrlMapping;
import com.example.exception.AliasAlreadyExistsException;
import com.example.exception.ShortUrlNotFoundException;
import com.example.repository.UrlClickRepository;
import com.example.repository.UrlMappingRepository;
import com.example.util.Base62;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UrlService {

    private final UrlMappingRepository mappingRepository;
    private final UrlClickRepository clickRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private static final String REDIS_PREFIX = "url:";

    public UrlService(UrlMappingRepository mappingRepository,
                      UrlClickRepository clickRepository,
                      RedisTemplate<String, String> redisTemplate) {
        this.mappingRepository = mappingRepository;
        this.clickRepository = clickRepository;
        this.redisTemplate = redisTemplate;
    }

    public String shortenUrl(ShortenRequest request) {
        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            if (mappingRepository.existsByShortKey(request.getCustomAlias())) {
                throw new AliasAlreadyExistsException(
                        "Custom alias is already taken: " + request.getCustomAlias());
            }
            return saveMapping(request.getCustomAlias(), request.getLongUrl(), request.getExpiresAt());
        }

        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl(request.getLongUrl());
        mapping.setCreatedAt(LocalDateTime.now());
        mapping.setExpiresAt(request.getExpiresAt());
        mapping.setShortKey("temp");

        mapping = mappingRepository.save(mapping);
        String shortKey = Base62.encode(mapping.getId());
        mapping.setShortKey(shortKey);
        mappingRepository.save(mapping);

        cacheUrl(shortKey, request.getLongUrl(), request.getExpiresAt());
        return shortKey;
    }

    public Optional<String> getLongUrl(String shortKey) {
        String cachedUrl = redisTemplate.opsForValue().get(REDIS_PREFIX + shortKey);
        if (cachedUrl != null) {
            return Optional.of(cachedUrl);
        }

        return mappingRepository.findByShortKey(shortKey).flatMap(mapping -> {
            if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(LocalDateTime.now())) {
                return Optional.empty();
            }
            cacheUrl(shortKey, mapping.getLongUrl(), mapping.getExpiresAt());
            return Optional.of(mapping.getLongUrl());
        });
    }

    public AnalyticsResponse getAnalytics(String shortKey) {
        UrlMapping mapping = mappingRepository.findByShortKey(shortKey)
                .orElseThrow(() -> new ShortUrlNotFoundException("Short URL not found: " + shortKey));

        long totalClicks = clickRepository.countByShortKey(shortKey);
        List<UrlClick> clicks = clickRepository.findByShortKey(shortKey);

        Map<String, Long> userAgents = clicks.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getUserAgent() != null ? c.getUserAgent() : "Unknown",
                        Collectors.counting()
                ));

        return new AnalyticsResponse(
                shortKey,
                mapping.getLongUrl(),
                totalClicks,
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                userAgents
        );
    }

    private String saveMapping(String shortKey, String longUrl, LocalDateTime expiresAt) {
        UrlMapping mapping = new UrlMapping(shortKey, longUrl, LocalDateTime.now(), expiresAt);
        try {
            mappingRepository.save(mapping);
        } catch (DataIntegrityViolationException ex) {
            // Closes the check-then-act race window between existsByShortKey() (in
            // shortenUrl()) and this save(): if a concurrent request inserted the same
            // alias in between, the DB's unique constraint on shortKey rejects this
            // insert. Re-map that to the same 409 the upfront check would have produced,
            // rather than letting a raw persistence exception surface as an opaque 500.
            throw new AliasAlreadyExistsException("Custom alias is already taken: " + shortKey);
        }
        cacheUrl(shortKey, longUrl, expiresAt);
        return shortKey;
    }

    private void cacheUrl(String shortKey, String longUrl, LocalDateTime expiresAt) {
        if (expiresAt != null) {
            long secondsUntilExpiry = Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
            if (secondsUntilExpiry > 0) {
                redisTemplate.opsForValue().set(REDIS_PREFIX + shortKey, longUrl, Duration.ofSeconds(secondsUntilExpiry));
            }
        } else {
            redisTemplate.opsForValue().set(REDIS_PREFIX + shortKey, longUrl, Duration.ofDays(7));
        }
    }
}