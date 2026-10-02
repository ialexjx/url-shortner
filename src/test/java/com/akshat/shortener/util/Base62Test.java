package com.akshat.shortener.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Base62 encoding and decoding
 */
class Base62Test {

    @Test
    @DisplayName("Encode zero should return '0'")
    void testEncodeZero() {
        assertEquals("0", Base62.encode(0));
    }

    @Test
    @DisplayName("Encode positive numbers correctly")
    void testEncodePositive() {
        String encoded = Base62.encode(125193);
        assertNotNull(encoded);
        assertFalse(encoded.isEmpty());
        // Verify decode brings back original
        assertEquals(125193, Base62.decode(encoded));
    }

    @Test
    @DisplayName("Encode and decode round-trip for various numbers")
    void testRoundTrip() {
        long[] testValues = {1, 61, 62, 1000, 999999, 10000000L, Long.MAX_VALUE / 100};
        for (long val : testValues) {
            String enc = Base62.encode(val);
            long dec = Base62.decode(enc);
            assertEquals(val, dec, "Roundtrip failed for: " + val);
        }
    }

    @Test
    @DisplayName("Negative ID should throw IllegalArgumentException")
    void testNegativeIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-5));
    }

    @Test
    @DisplayName("Invalid characters in decode should throw IllegalArgumentException")
    void testInvalidDecodeChars() {
        assertThrows(IllegalArgumentException.class, () -> Base62.decode("abc@#$"));
    }
}
