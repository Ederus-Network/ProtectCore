/*
 * Decompiled with CFR 0.152.
 */
package com.lucidaps.db;

public final class PurgeResult {
    public final boolean dryRun;
    public final long cutoffMillis;
    public final long actionsAffected;
    public final long snapshotsByAgeAffected;
    public final long orphanSnapshotsAffected;
    public final long elapsedMs;

    public PurgeResult(boolean bl, long l, long l2, long l3, long l4, long l5) {
        this.dryRun = bl;
        this.cutoffMillis = l;
        this.actionsAffected = l2;
        this.snapshotsByAgeAffected = l3;
        this.orphanSnapshotsAffected = l4;
        this.elapsedMs = l5;
    }
}

