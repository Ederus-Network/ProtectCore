/*
 * Decompiled with CFR 0.152.
 */
package com.lucidaps.utilities;

public class TimeUtil {
    public static long parseTimePeriod(String string) {
        long l;
        if (string == null || string.length() < 2) {
            return -1L;
        }
        char c = Character.toLowerCase(string.charAt(string.length() - 1));
        String string2 = string.substring(0, string.length() - 1);
        switch (c) {
            case 'd': {
                l = 86400000L;
                break;
            }
            case 'h': {
                l = 3600000L;
                break;
            }
            case 'm': {
                l = 60000L;
                break;
            }
            case 's': {
                l = 1000L;
                break;
            }
            default: {
                return -1L;
            }
        }
        try {
            long l2 = Long.parseLong(string2);
            return l2 * l;
        }
        catch (NumberFormatException numberFormatException) {
            return -1L;
        }
    }
}

