package com.example.exception;

/**
 * Thrown when a short key doesn't correspond to any known (or unexpired) URL mapping.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException(String message) {
        super(message);
    }
}
