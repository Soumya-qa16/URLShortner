package com.example.exception;

import com.example.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit tests for the handler methods themselves, independent of MockMvc/Spring
 * dispatch (which is covered separately in UrlControllerTest). This isolates the
 * status-code and body-construction logic.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404WithExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new ShortUrlNotFoundException("Short URL not found: xyz"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("Short URL not found: xyz");
    }

    @Test
    void handleAliasConflict_returns409WithExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleAliasConflict(new AliasAlreadyExistsException("Custom alias is already taken: taken"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Custom alias is already taken: taken");
    }

    @Test
    void handleBadArgument_returns400WithExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadArgument(new IllegalArgumentException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
    }

    @Test
    void handleUnexpected_returns500AndNeverLeaksTheOriginalExceptionMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnexpected(new RuntimeException("sensitive internal stack detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).doesNotContain("sensitive internal stack detail");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred.");
    }
}
