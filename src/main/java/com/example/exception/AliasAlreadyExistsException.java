package com.example.exception;

/**
 * Thrown when a requested custom alias is already taken by another mapping.
 * Mapped to HTTP 409 (Conflict) by {@link GlobalExceptionHandler} -- distinct from
 * {@link ShortUrlNotFoundException} because "already exists" and "doesn't exist" are
 * different client-facing conditions and shouldn't share a status code just because
 * they both happened to be IllegalArgumentException before this fix.
 */
public class AliasAlreadyExistsException extends RuntimeException {

    public AliasAlreadyExistsException(String message) {
        super(message);
    }
}
