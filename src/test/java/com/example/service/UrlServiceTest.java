package com.example.service;

import com.example.dto.AnalyticsResponse;
import com.example.dto.ShortenRequest;
import com.example.entity.UrlClick;
import com.example.entity.UrlMapping;
import com.example.exception.AliasAlreadyExistsException;
import com.example.exception.ShortUrlNotFoundException;
import com.example.repository.UrlClickRepository;
import com.example.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UrlService}. All collaborators (repositories, RedisTemplate)
 * are mocked so these run without a database or Redis instance.
 */
@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlMappingRepository mappingRepository;
    @Mock
    private UrlClickRepository clickRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private UrlService urlService;

    @BeforeEach
    void setUp() {
        urlService = new UrlService(mappingRepository, clickRepository, redisTemplate);
    }

    // ---------------------------------------------------------------
    // shortenUrl()
    // ---------------------------------------------------------------

    @Test
    void shortenUrl_withAvailableCustomAlias_savesAndReturnsAlias() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(mappingRepository.existsByShortKey("my-alias")).thenReturn(false);
        ShortenRequest request = new ShortenRequest("https://example.com/page", "my-alias", null);

        String result = urlService.shortenUrl(request);

        assertThat(result).isEqualTo("my-alias");
        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(mappingRepository).save(captor.capture());
        assertThat(captor.getValue().getShortKey()).isEqualTo("my-alias");
        assertThat(captor.getValue().getLongUrl()).isEqualTo("https://example.com/page");
        // No expiry supplied -> falls back to the 7-day default cache TTL.
        verify(valueOperations).set(eq("url:my-alias"), eq("https://example.com/page"), eq(Duration.ofDays(7)));
    }

    @Test
    void shortenUrl_withTakenCustomAlias_throwsAliasAlreadyExistsAndNeverSaves() {
        when(mappingRepository.existsByShortKey("taken")).thenReturn(true);
        ShortenRequest request = new ShortenRequest("https://example.com/page", "taken", null);

        assertThrows(AliasAlreadyExistsException.class, () -> urlService.shortenUrl(request));

        verify(mappingRepository, never()).save(any());
    }

    @Test
    void shortenUrl_concurrentAliasInsertRace_mapsDbConstraintViolationToAliasAlreadyExists() {
        // Simulates the check-then-act race: existsByShortKey() passes (no row yet),
        // but a concurrent request wins the insert first, so THIS save() hits the DB's
        // unique constraint on shortKey and Spring Data wraps it as
        // DataIntegrityViolationException. UrlService must translate that into the same
        // AliasAlreadyExistsException (-> 409) the upfront check would have produced,
        // not let it propagate as a raw persistence exception.
        when(mappingRepository.existsByShortKey("racy-alias")).thenReturn(false);
        when(mappingRepository.save(any(UrlMapping.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        ShortenRequest request = new ShortenRequest("https://example.com/page", "racy-alias", null);

        assertThrows(AliasAlreadyExistsException.class, () -> urlService.shortenUrl(request));

        // The race is specific to the DB constraint -- caching must not happen for a
        // save that never actually persisted.
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void shortenUrl_withBlankCustomAlias_treatsItAsAbsentAndGeneratesKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(mappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(42L); // simulate DB-assigned identity on first insert
            }
            return saved;
        });
        // Whitespace-only alias should be treated the same as "no alias" (String.isBlank()).
        ShortenRequest request = new ShortenRequest("https://example.com/page", "   ", null);

        String result = urlService.shortenUrl(request);

        assertThat(result).isEqualTo("G"); // Base62.encode(42) == "G"
        verify(mappingRepository, never()).existsByShortKey(anyString());
        verify(mappingRepository, times(2)).save(any(UrlMapping.class));
    }

    @Test
    void shortenUrl_withoutCustomAlias_generatesKeyFromPersistedId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(mappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved;
        });
        ShortenRequest request = new ShortenRequest("https://example.com/page", null, null);

        String result = urlService.shortenUrl(request);

        assertThat(result).isEqualTo("1"); // Base62.encode(1) == "1"
        // Saved once to obtain the generated ID, once again to persist the derived shortKey.
        verify(mappingRepository, times(2)).save(any(UrlMapping.class));
        verify(valueOperations).set(eq("url:1"), eq("https://example.com/page"), eq(Duration.ofDays(7)));
    }

    @Test
    void shortenUrl_withFutureExpiry_cachesWithTtlDerivedFromExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(mappingRepository.existsByShortKey("future")).thenReturn(false);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        ShortenRequest request = new ShortenRequest("https://example.com/page", "future", expiresAt);

        urlService.shortenUrl(request);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq("url:future"), eq("https://example.com/page"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue().toMinutes()).isBetween(58L, 60L);
    }

    @Test
    void shortenUrl_withAlreadyExpiredExpiry_persistsMappingButSkipsCaching() {
        when(mappingRepository.existsByShortKey("expired")).thenReturn(false);
        ShortenRequest request = new ShortenRequest(
                "https://example.com/page", "expired", LocalDateTime.now().minusMinutes(5));

        urlService.shortenUrl(request);

        // secondsUntilExpiry <= 0 -> cacheUrl's guard skips the Redis write entirely.
        verify(redisTemplate, never()).opsForValue();
        verify(mappingRepository).save(any(UrlMapping.class));
    }

    // ---------------------------------------------------------------
    // getLongUrl()
    // ---------------------------------------------------------------

    @Test
    void getLongUrl_cacheHit_returnsFromRedisWithoutQueryingDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abc123")).thenReturn("https://cached.example.com");

        Optional<String> result = urlService.getLongUrl("abc123");

        assertThat(result).contains("https://cached.example.com");
        verify(mappingRepository, never()).findByShortKey(anyString());
    }

    @Test
    void getLongUrl_cacheMissFoundAndNotExpired_returnsUrlAndRepopulatesCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abc123")).thenReturn(null);
        UrlMapping mapping = new UrlMapping("abc123", "https://example.com", LocalDateTime.now(), null);
        when(mappingRepository.findByShortKey("abc123")).thenReturn(Optional.of(mapping));

        Optional<String> result = urlService.getLongUrl("abc123");

        assertThat(result).contains("https://example.com");
        verify(valueOperations).set(eq("url:abc123"), eq("https://example.com"), any(Duration.class));
    }

    @Test
    void getLongUrl_cacheMissFoundButExpired_returnsEmptyAndDoesNotRecache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:old")).thenReturn(null);
        UrlMapping mapping = new UrlMapping(
                "old", "https://example.com", LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
        when(mappingRepository.findByShortKey("old")).thenReturn(Optional.of(mapping));

        Optional<String> result = urlService.getLongUrl("old");

        assertThat(result).isEmpty();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getLongUrl_cacheMissAndNotFound_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:missing")).thenReturn(null);
        when(mappingRepository.findByShortKey("missing")).thenReturn(Optional.empty());

        Optional<String> result = urlService.getLongUrl("missing");

        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------
    // getAnalytics()
    // ---------------------------------------------------------------

    @Test
    void getAnalytics_existingKeyWithClicks_aggregatesUserAgentCountsAndGroupsNullsAsUnknown() {
        UrlMapping mapping = new UrlMapping("abc123", "https://example.com", LocalDateTime.now().minusDays(1), null);
        when(mappingRepository.findByShortKey("abc123")).thenReturn(Optional.of(mapping));
        when(clickRepository.countByShortKey("abc123")).thenReturn(3L);
        when(clickRepository.findByShortKey("abc123")).thenReturn(List.of(
                new UrlClick("abc123", "1.1.1.1", "Chrome", LocalDateTime.now()),
                new UrlClick("abc123", "2.2.2.2", "Chrome", LocalDateTime.now()),
                new UrlClick("abc123", "3.3.3.3", null, LocalDateTime.now())
        ));

        AnalyticsResponse response = urlService.getAnalytics("abc123");

        assertThat(response.getTotalClicks()).isEqualTo(3L);
        assertThat(response.getUserAgents()).containsEntry("Chrome", 2L);
        assertThat(response.getUserAgents()).containsEntry("Unknown", 1L);
        assertThat(response.getLongUrl()).isEqualTo("https://example.com");
    }

    @Test
    void getAnalytics_existingKeyWithNoClicks_returnsZeroCountAndEmptyMap() {
        UrlMapping mapping = new UrlMapping("nokey", "https://example.com", LocalDateTime.now(), null);
        when(mappingRepository.findByShortKey("nokey")).thenReturn(Optional.of(mapping));
        when(clickRepository.countByShortKey("nokey")).thenReturn(0L);
        when(clickRepository.findByShortKey("nokey")).thenReturn(List.of());

        AnalyticsResponse response = urlService.getAnalytics("nokey");

        assertThat(response.getTotalClicks()).isZero();
        assertThat(response.getUserAgents()).isEmpty();
    }

    @Test
    void getAnalytics_unknownShortKey_throwsShortUrlNotFoundAndNeverQueriesClicks() {
        when(mappingRepository.findByShortKey("unknown")).thenReturn(Optional.empty());

        assertThrows(ShortUrlNotFoundException.class, () -> urlService.getAnalytics("unknown"));
        verify(clickRepository, never()).findByShortKey(anyString());
        verify(clickRepository, never()).countByShortKey(anyString());
    }
}
