package com.akshat.shortener.util;

/**
 * ==============================================================================
 * Base62 Encoding & Decoding Utility
 * ==============================================================================
 * 
 * Kyun Base62 choose kiya Base64 ya Hex ke bajaye?
 * 1. Base64 me '+' aur '/' special characters aate hain jo URLs me escape karne
 *    padte hain (%2B, %2F), jisse URL lambi aur gandi dikhti hai.
 * 2. Hexadecimal (0-9, a-f) me sirf 16 characters hote hain, toh 1 million URLs
 *    ke liye lamba string chahiye hoga.
 * 3. Base62 me strictly [0-9, a-z, A-Z] use hota hai jo 100% URL-safe hai.
 * 
 * Mathematical Capacity:
 * 6 characters Base62 = 62^6 = 56.8 Billion unique URLs
 * 7 characters Base62 = 62^7 = 3.52 Trillion unique URLs
 * 
 * Is algorithm ka time complexity O(log62(N)) hai jo negligible (< 1 microsecond) hai.
 */
public final class Base62 {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length(); // 62

    private Base62() {
        // Utility class, instantiate karne ki zaroorat nahi hai
    }

    /**
     * Positive Long ID ko Base62 string me convert karta hai.
     * E.g. 125193 -> "wU1"
     */
    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("ID negative nahi ho sakta: " + id);
        }
        if (id == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            int remainder = (int) (id % BASE);
            sb.append(ALPHABET.charAt(remainder));
            id /= BASE;
        }

        // Modulo se characters reverse order me aate hain, isliye reverse karna zaroori hai
        return sb.reverse().toString();
    }

    /**
     * Base62 string ko waapas original Long ID me decode karta hai.
     * Useful for reverse lookup ya debugging.
     */
    public static long decode(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("String empty nahi ho sakta");
        }

        long result = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int digit = ALPHABET.indexOf(c);
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
