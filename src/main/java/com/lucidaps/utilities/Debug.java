/*
 * Decompiled with CFR 0.152.
 */
package com.lucidaps.utilities;

import com.lucidaps.ProtectCore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Debug {
    private static final Map<String, RL> RL_MAP = new ConcurrentHashMap<String, RL>();

    private Debug() {
    }

    public static boolean on(ProtectCore protectCore, String string) {
        if (protectCore == null) {
            return false;
        }
        if (!protectCore.getConfig().getBoolean("debug.enabled", false)) {
            return false;
        }
        return protectCore.getConfig().getBoolean("debug." + string, false);
    }

    private static RL rl(ProtectCore protectCore, String string2) {
        int n = Math.max(1, protectCore.getConfig().getInt("debug.ratePerSec", 20));
        return RL_MAP.computeIfAbsent(string2, string -> new RL(n));
    }

    public static void log(ProtectCore protectCore, String string, String string2, Object ... objectArray) {
        if (!Debug.on(protectCore, string)) {
            return;
        }
        if (Debug.rl(protectCore, string).allow()) {
            protectCore.getLogger().info("[DBG][" + string + "] " + String.format(string2, objectArray));
        }
    }

    public static void warn(ProtectCore protectCore, String string, String string2, Object ... objectArray) {
        if (!Debug.on(protectCore, string)) {
            return;
        }
        if (Debug.rl(protectCore, string).allow()) {
            protectCore.getLogger().warning("[DBG][" + string + "] " + String.format(string2, objectArray));
        }
    }

    public static long tic() {
        return System.nanoTime();
    }

    public static String toc(long l) {
        return String.format("%.3f ms", (double)(System.nanoTime() - l) / 1000000.0);
    }

    public static Ctx ctx(ProtectCore protectCore) {
        return new Ctx(protectCore);
    }

    public static Scope scope(ProtectCore protectCore, String string, Ctx ctx, String string2, Object ... objectArray) {
        return new Scope(protectCore, string, ctx, String.format(string2, objectArray));
    }

    private static final class RL {
        final int perSec;
        volatile long windowStart = System.currentTimeMillis();
        final AtomicInteger used = new AtomicInteger(0);
        final AtomicLong windowStartNs = new AtomicLong(System.nanoTime());

        RL(int n) {
            this.perSec = n;
        }

        boolean allow() {
            long l;
            long l2 = System.nanoTime();
            if (l2 - (l = this.windowStartNs.get()) >= 1000000000L && this.windowStartNs.compareAndSet(l, l2)) {
                this.used.set(0);
            }
            return this.used.incrementAndGet() <= this.perSec;
        }
    }

    public static final class Ctx {
        private final ProtectCore p;
        private final Map<String, String> kv = new LinkedHashMap<String, String>();

        private Ctx(ProtectCore protectCore) {
            this.p = protectCore;
        }

        public Ctx with(String string, Object object) {
            if (object != null) {
                this.kv.put(string, String.valueOf(object));
            }
            return this;
        }

        public String toString() {
            if (this.kv.isEmpty()) {
                return "";
            }
            StringBuilder stringBuilder = new StringBuilder("[");
            boolean bl = true;
            for (Map.Entry<String, String> entry : this.kv.entrySet()) {
                if (!bl) {
                    stringBuilder.append(' ');
                }
                stringBuilder.append(entry.getKey()).append('=').append(entry.getValue());
                bl = false;
            }
            return stringBuilder.append(']').toString();
        }

        public void log(String string, String string2, Object ... objectArray) {
            Debug.log(this.p, string, this.toString() + " " + String.format(string2, objectArray), new Object[0]);
        }

        public void warn(String string, String string2, Object ... objectArray) {
            Debug.warn(this.p, string, this.toString() + " " + String.format(string2, objectArray), new Object[0]);
        }
    }

    public static final class Scope
    implements AutoCloseable {
        private final ProtectCore p;
        private final String key;
        private final String label;
        private final long t0 = System.nanoTime();
        private final Map<String, Long> counters = new LinkedHashMap<String, Long>();
        private final Ctx ctx;

        Scope(ProtectCore protectCore, String string, Ctx ctx, String string2) {
            this.p = protectCore;
            this.key = string;
            this.ctx = ctx;
            this.label = string2;
        }

        public Scope inc(String string) {
            this.counters.merge(string, 1L, Long::sum);
            return this;
        }

        public Scope add(String string, long l) {
            this.counters.merge(string, l, Long::sum);
            return this;
        }

        @Override
        public void close() {
            if (!Debug.on(this.p, this.key)) {
                return;
            }
            StringBuilder stringBuilder = new StringBuilder(this.label);
            if (!this.counters.isEmpty()) {
                stringBuilder.append(" | ");
                boolean bl = true;
                for (Map.Entry<String, Long> entry : this.counters.entrySet()) {
                    if (!bl) {
                        stringBuilder.append(", ");
                    }
                    stringBuilder.append(entry.getKey()).append("=").append(entry.getValue());
                    bl = false;
                }
            }
            stringBuilder.append(" | took=").append(String.format("%.3f ms", (double)(System.nanoTime() - this.t0) / 1000000.0));
            Debug.log(this.p, this.key, (String)(this.ctx == null ? "" : this.ctx.toString() + " ") + String.valueOf(stringBuilder), new Object[0]);
        }
    }
}

