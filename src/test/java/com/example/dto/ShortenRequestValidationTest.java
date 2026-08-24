package com.example.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the Bean Validation constraints declared on {@link ShortenRequest} directly,
 * independent of Spring MVC. Complements the 400-status assertions in
 * UrlControllerTest by pinning down exactly which constraint fires and why.
 */
class ShortenRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    void fullyPopulatedRequest_hasNoViolations() {
        ShortenRequest request = new ShortenRequest(
                "https://example.com/page", "alias", java.time.LocalDateTime.now().plusDays(1));

        Set<ConstraintViolation<ShortenRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void minimalValidRequest_customAliasAndExpiryAreOptional() {
        ShortenRequest request = new ShortenRequest("https://example.com/page", null, null);

        Set<ConstraintViolation<ShortenRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void blankLongUrl_violatesNotBlank() {
        ShortenRequest request = new ShortenRequest("", null, null);

        Set<ConstraintViolation<ShortenRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("longUrl"));
    }

    @Test
    void nullLongUrl_violatesNotBlank() {
        ShortenRequest request = new ShortenRequest(null, null, null);

        Set<ConstraintViolation<ShortenRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void malformedLongUrl_violatesUrlConstraint() {
        ShortenRequest request = new ShortenRequest("definitely-not-a-url", null, null);

        Set<ConstraintViolation<ShortenRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }
}
