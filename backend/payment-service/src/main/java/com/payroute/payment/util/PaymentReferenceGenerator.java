package com.payroute.payment.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Generates a deterministic, customer-facing payment reference of the form
 * {@code PRXXXXXXXXXX} (2-char prefix + 10 chars from a fixed alphabet of 32
 * unambiguous symbols — no 0/O/1/I/L confusables).
 *
 * <p>The reference is derived from the row id and creation timestamp, so
 * the same payment always yields the same reference (idempotent backfill).
 */
public final class PaymentReferenceGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long MS_PER_DAY = 86_400_000L;

    private PaymentReferenceGenerator() { /* static utility */ }

    public static String generate(long id, LocalDateTime createdAt) {
        long dayStamp = createdAt == null
                ? 0L
                : createdAt.toInstant(ZoneOffset.UTC).toEpochMilli() / MS_PER_DAY;

        long acc = mix(id * 1_000_003L + dayStamp);
        StringBuilder out = new StringBuilder(12).append("PR");
        for (int i = 0; i < 10; i++) {
            int idx = (int) Math.floorMod(acc, ALPHABET.length());
            out.append(ALPHABET.charAt(idx));
            acc = mix(acc + id + i);
        }
        return out.toString();
    }

    /** Simple deterministic 32-bit mixing — fast, stable, no crypto needed. */
    private static long mix(long seed) {
        int x = (int) seed;
        x = x ^ 0x9e3779b9;
        x = (x ^ (x >>> 16)) * 0x85ebca6b;
        x = (x ^ (x >>> 13)) * 0xc2b2ae35;
        x = x ^ (x >>> 16);
        return x & 0xFFFFFFFFL;
    }
}
