package org.ecom.order;

import java.security.SecureRandom;

/**
 * Human-friendly order references such as {@code ORD-7KQ2M9XH4C}, without ambiguous characters (0/O, 1/I/L).
 */
final class OrderReferences {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final int LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderReferences() {
    }

    static String next() {
        var reference = new StringBuilder("ORD-");
        for (int i = 0; i < LENGTH; i++) {
            reference.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }

}
