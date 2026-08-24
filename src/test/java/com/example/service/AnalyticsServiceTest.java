package com.example.service;

import com.example.entity.UrlClick;
import com.example.repository.UrlClickRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AnalyticsService}.
 *
 * NOTE on scope: logClickAsync() is called directly here, bypassing Spring's proxy,
 * so @Async has no effect in this test -- it runs synchronously on the test thread.
 * That's fine for verifying the persistence logic, but it does NOT prove the method
 * actually executes off the calling (request) thread in production. That would require
 * a slower @SpringBootTest with a real (or test) TaskExecutor -- out of scope for a
 * pure unit test, called out here as a known limitation.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private UrlClickRepository clickRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(clickRepository);
    }

    @Test
    void logClickAsync_persistsClickWithSuppliedDetailsAndCurrentTimestamp() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        analyticsService.logClickAsync("abc123", "192.168.0.1", "Mozilla/5.0");

        ArgumentCaptor<UrlClick> captor = ArgumentCaptor.forClass(UrlClick.class);
        verify(clickRepository).save(captor.capture());
        UrlClick saved = captor.getValue();

        assertThat(saved.getShortKey()).isEqualTo("abc123");
        assertThat(saved.getIpAddress()).isEqualTo("192.168.0.1");
        assertThat(saved.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(saved.getClickedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void logClickAsync_withNullIpAndUserAgent_stillPersistsClick() {
        analyticsService.logClickAsync("abc123", null, null);

        ArgumentCaptor<UrlClick> captor = ArgumentCaptor.forClass(UrlClick.class);
        verify(clickRepository).save(captor.capture());
        assertThat(captor.getValue().getIpAddress()).isNull();
        assertThat(captor.getValue().getUserAgent()).isNull();
        assertThat(captor.getValue().getShortKey()).isEqualTo("abc123");
    }
}
