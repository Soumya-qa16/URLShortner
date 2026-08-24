package com.example.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Base62}. Pure function, no mocking required.
 */
class Base62Test {

    @Test
    void encode_zero_returnsFirstAlphabetCharacter() {
        assertThat(Base62.encode(0)).isEqualTo("0");
    }

    @ParameterizedTest(name = "encode({0}) == \"{1}\"")
    @CsvSource({
            "1, 1",
            "9, 9",
            "10, a",
            "35, z",
            "36, A",
            "61, Z"
    })
    void encode_singleDigitValues_mapToExpectedAlphabetCharacter(long input, String expected) {
        assertThat(Base62.encode(input)).isEqualTo(expected);
    }

    @Test
    void encode_valueEqualToBase_rollsOverToTwoCharacters() {
        // 62 = 1*62 + 0 -> most significant digit '1', least significant '0' -> "10"
        assertThat(Base62.encode(62)).isEqualTo("10");
    }

    @Test
    void encode_valueJustAboveBase_incrementsLeastSignificantDigit() {
        // 63 = 1*62 + 1 -> "11"
        assertThat(Base62.encode(63)).isEqualTo("11");
    }

    @Test
    void encode_sameInputTwice_isDeterministic() {
        assertThat(Base62.encode(123_456_789L)).isEqualTo(Base62.encode(123_456_789L));
    }

    @Test
    void encode_differentInputs_produceDifferentOutputs() {
        assertThat(Base62.encode(100)).isNotEqualTo(Base62.encode(101));
    }

    @Test
    void encode_maxLongValue_doesNotThrowAndProducesNonEmptyResult() {
        assertThat(Base62.encode(Long.MAX_VALUE)).isNotEmpty();
    }

    @Test
    void encode_negativeValue_currentlyReturnsEmptyString() {
        // Documents CURRENT behavior rather than desired behavior: the loop condition
        // is `while (num > 0)`, so a negative input skips the loop entirely and the
        // method returns "". Auto-increment IDs should never be negative in practice,
        // but Base62.encode() performs no input validation to guard against it.
        // Flagged as a risk/gap in the test summary rather than silently ignored.
        assertThat(Base62.encode(-5)).isEqualTo("");
    }
}
