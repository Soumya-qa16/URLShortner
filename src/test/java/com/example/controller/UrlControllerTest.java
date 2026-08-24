package com.example.controller;

import com.example.dto.AnalyticsResponse;
import com.example.dto.ShortenRequest;
import com.example.exception.AliasAlreadyExistsException;
import com.example.exception.GlobalExceptionHandler;
import com.example.exception.ShortUrlNotFoundException;
import com.example.service.AnalyticsService;
import com.example.service.UrlService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link UrlController} using a standalone MockMvc setup
 * (no full ApplicationContext / database / Redis needed). Service dependencies
 * are mocked so these tests focus purely on request mapping, status codes,
 * response shape, and validation wiring.
 */
@ExtendWith(MockitoExtension.class)
class UrlControllerTest {

    @Mock
    private UrlService urlService;
    @Mock
    private AnalyticsService analyticsService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        UrlController controller = new UrlController(urlService, analyticsService);
        // @Value isn't resolved outside a Spring context in a standalone MockMvc test.
        ReflectionTestUtils.setField(controller, "baseUrl", "http://localhost:8080/");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean()) // enables @Valid enforcement
                .setControllerAdvice(new GlobalExceptionHandler()) // exercises the real error mapping
                .build();
    }

    // ---------------------------------------------------------------
    // POST /api/v1/shorten
    // ---------------------------------------------------------------

    @Test
    void shortenUrl_validRequest_returnsShortKeyAndFullShortUrl() throws Exception {
        when(urlService.shortenUrl(any(ShortenRequest.class))).thenReturn("abc123");

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ShortenRequest("https://example.com/page", null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortKey").value("abc123"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc123"));
    }

    @Test
    void shortenUrl_blankLongUrl_returnsBadRequestWithFieldErrorBody() throws Exception {
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ShortenRequest("", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.longUrl").exists());

        verify(urlService, never()).shortenUrl(any());
    }

    @Test
    void shortenUrl_takenAlias_returns409WithErrorBody() throws Exception {
        when(urlService.shortenUrl(any(ShortenRequest.class)))
                .thenThrow(new AliasAlreadyExistsException("Custom alias is already taken: taken"));

        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ShortenRequest("https://example.com/page", "taken", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Custom alias is already taken: taken"));
    }

    @Test
    void shortenUrl_malformedUrl_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ShortenRequest("not-a-valid-url", null, null))))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------
    // GET /{shortKey}
    // ---------------------------------------------------------------

    @Test
    void redirect_knownShortKey_returns302WithLocationHeaderAndLogsClick() throws Exception {
        when(urlService.getLongUrl("abc123")).thenReturn(Optional.of("https://example.com/page"));

        mockMvc.perform(get("/abc123").header("User-Agent", "JUnit-Agent"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/page"));

        verify(analyticsService).logClickAsync(eq("abc123"), anyString(), eq("JUnit-Agent"));
    }

    @Test
    void redirect_unknownShortKey_returns404AndNeverLogsClick() throws Exception {
        when(urlService.getLongUrl("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(analyticsService);
    }

    // ---------------------------------------------------------------
    // GET /api/v1/analytics/{shortKey}
    // ---------------------------------------------------------------

    @Test
    void getAnalytics_existingShortKey_returnsAnalyticsPayload() throws Exception {
        AnalyticsResponse response = new AnalyticsResponse(
                "abc123", "https://example.com/page", 5L,
                LocalDateTime.now(), null, Map.of("Chrome", 5L));
        when(urlService.getAnalytics("abc123")).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortKey").value("abc123"))
                .andExpect(jsonPath("$.totalClicks").value(5));
    }

    @Test
    void getAnalytics_unknownShortKey_returns404WithErrorBody() throws Exception {
        // Previously this exception had no handler and surfaced as an opaque 500
        // (see git history / TESTING_SUMMARY.md for the original gap this closes).
        // GlobalExceptionHandler now maps ShortUrlNotFoundException -> 404.
        when(urlService.getAnalytics("missing"))
                .thenThrow(new ShortUrlNotFoundException("Short URL not found: missing"));

        mockMvc.perform(get("/api/v1/analytics/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Short URL not found: missing"));
    }
}
