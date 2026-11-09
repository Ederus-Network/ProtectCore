/*
 * Decompiled with CFR 0.152.
 */
package com.lucidaps.web;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class EditorAccessTokenStore {
    private static final int TOKEN_BYTES = 32;
    private final ConcurrentHashMap<String, Long> activeTokens = new ConcurrentHashMap();
    private final SecureRandom secureRandom;
    private final LongSupplier currentTimeMillis;
    private final long tokenValidityMs;

    EditorAccessTokenStore(long l) {
        this(l, System::currentTimeMillis, new SecureRandom());
    }

    EditorAccessTokenStore(long l, LongSupplier longSupplier, SecureRandom secureRandom) {
        if (l < 1L) {
            throw new IllegalArgumentException("tokenTtlMinutes must be greater than zero");
        }
        this.tokenValidityMs = TimeUnit.MINUTES.toMillis(l);
        this.currentTimeMillis = longSupplier;
        this.secureRandom = secureRandom;
    }

    String issueToken() {
        byte[] byArray;
        String string;
        long l = this.currentTimeMillis.getAsLong();
        long l2 = this.tokenValidityMs >= Long.MAX_VALUE - l ? Long.MAX_VALUE : l + this.tokenValidityMs;
        do {
            byArray = new byte[32];
            this.secureRandom.nextBytes(byArray);
        } while (this.activeTokens.putIfAbsent(string = Base64.getUrlEncoder().withoutPadding().encodeToString(byArray), l2) != null);
        return string;
    }

    boolean isValid(String string) {
        if (string == null || string.isBlank()) {
            return false;
        }
        long l = this.currentTimeMillis.getAsLong();
        Long l2 = this.activeTokens.get(string);
        if (l2 == null) {
            return false;
        }
        if (l2 < l) {
            this.activeTokens.remove(string, l2);
            return false;
        }
        return true;
    }

    int purgeExpired(long l) {
        int n = this.activeTokens.size();
        this.activeTokens.entrySet().removeIf(entry -> (Long)entry.getValue() < l);
        return n - this.activeTokens.size();
    }
}

