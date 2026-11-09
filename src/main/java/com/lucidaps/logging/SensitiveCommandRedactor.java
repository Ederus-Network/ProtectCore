/*
 * Decompiled with CFR 0.152.
 */
package com.lucidaps.logging;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

public final class SensitiveCommandRedactor {
    private SensitiveCommandRedactor() {
    }

    public static String normalize(String string) {
        if (string == null) {
            return "";
        }
        String string2 = string.trim();
        if (string2.startsWith("/")) {
            string2 = string2.substring(1);
        }
        return string2.toLowerCase(Locale.ROOT).replace(':', ' ').replaceAll("\\s+", " ").trim();
    }

    public static Optional<String> longestPrefixMatch(Collection<String> collection, String string) {
        String string2 = null;
        for (String string3 : collection) {
            if (!string.equals(string3) && !string.startsWith(string3 + " ") || string2 != null && string3.length() <= string2.length()) continue;
            string2 = string3;
        }
        return Optional.ofNullable(string2);
    }

    public static Optional<Redaction> redact(String string2, Collection<String> collection) {
        String string3 = SensitiveCommandRedactor.normalize(string2);
        return SensitiveCommandRedactor.longestPrefixMatch(collection, string3).map(string -> new Redaction((String)string, "/" + string + " [REDACTED]"));
    }

    public record Redaction(String command, String storedText) {
    }
}

