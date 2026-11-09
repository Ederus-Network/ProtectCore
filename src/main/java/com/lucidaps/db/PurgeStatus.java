/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nullable
 */
package com.lucidaps.db;

import javax.annotation.Nullable;

public final class PurgeStatus {
    public final boolean running;
    public final Stage stage;
    public final int keepDays;
    public final long cutoffMillis;
    public final long startedAtMs;
    public final long endedAtMs;
    public final long lastUpdateAtMs;
    public final long actionsDeleted;
    public final long snapshotsByAgeDeleted;
    public final long orphanSnapshotsDeleted;
    public final int batchSize;
    public final boolean maintenanceMode;
    @Nullable
    public final String startedBy;
    @Nullable
    public final String lastMessage;
    @Nullable
    public final String lastError;

    private PurgeStatus(boolean bl, Stage stage, int n, long l, long l2, long l3, long l4, long l5, long l6, long l7, int n2, boolean bl2, @Nullable String string, @Nullable String string2, @Nullable String string3) {
        this.running = bl;
        this.stage = stage;
        this.keepDays = n;
        this.cutoffMillis = l;
        this.startedAtMs = l2;
        this.endedAtMs = l3;
        this.lastUpdateAtMs = l4;
        this.actionsDeleted = l5;
        this.snapshotsByAgeDeleted = l6;
        this.orphanSnapshotsDeleted = l7;
        this.batchSize = n2;
        this.maintenanceMode = bl2;
        this.startedBy = string;
        this.lastMessage = string2;
        this.lastError = string3;
    }

    public static PurgeStatus idle() {
        long l = System.currentTimeMillis();
        return new PurgeStatus(false, Stage.IDLE, 0, 0L, 0L, 0L, l, 0L, 0L, 0L, 0, false, null, null, null);
    }

    public PurgeStatus withMessage(String string) {
        long l = System.currentTimeMillis();
        return new PurgeStatus(this.running, this.stage, this.keepDays, this.cutoffMillis, this.startedAtMs, this.endedAtMs, l, this.actionsDeleted, this.snapshotsByAgeDeleted, this.orphanSnapshotsDeleted, this.batchSize, this.maintenanceMode, this.startedBy, string, this.lastError);
    }

    public PurgeStatus withStage(Stage stage) {
        long l = System.currentTimeMillis();
        return new PurgeStatus(this.running, stage, this.keepDays, this.cutoffMillis, this.startedAtMs, this.endedAtMs, l, this.actionsDeleted, this.snapshotsByAgeDeleted, this.orphanSnapshotsDeleted, this.batchSize, this.maintenanceMode, this.startedBy, this.lastMessage, this.lastError);
    }

    public PurgeStatus withCounts(long l, long l2, long l3) {
        long l4 = System.currentTimeMillis();
        return new PurgeStatus(this.running, this.stage, this.keepDays, this.cutoffMillis, this.startedAtMs, this.endedAtMs, l4, l, l2, l3, this.batchSize, this.maintenanceMode, this.startedBy, this.lastMessage, this.lastError);
    }

    public PurgeStatus markStart(int n, long l, int n2, boolean bl, @Nullable String string) {
        long l2 = System.currentTimeMillis();
        Stage stage = n == 0 ? Stage.RESETTING_SCHEMA : Stage.DELETING_ACTIONS;
        return new PurgeStatus(true, stage, n, l, l2, 0L, l2, 0L, 0L, 0L, n2, bl, string, n == 0 ? "Reset starting\u2026" : "Purge starting\u2026", null);
    }

    public PurgeStatus markDone() {
        long l = System.currentTimeMillis();
        return new PurgeStatus(false, Stage.DONE, this.keepDays, this.cutoffMillis, this.startedAtMs, l, l, this.actionsDeleted, this.snapshotsByAgeDeleted, this.orphanSnapshotsDeleted, this.batchSize, this.maintenanceMode, this.startedBy, "Purge complete.", null);
    }

    public PurgeStatus markCancelled() {
        long l = System.currentTimeMillis();
        return new PurgeStatus(false, Stage.CANCELLED, this.keepDays, this.cutoffMillis, this.startedAtMs, l, l, this.actionsDeleted, this.snapshotsByAgeDeleted, this.orphanSnapshotsDeleted, this.batchSize, this.maintenanceMode, this.startedBy, "Purge cancelled.", null);
    }

    public PurgeStatus markFailed(String string) {
        long l = System.currentTimeMillis();
        return new PurgeStatus(false, Stage.FAILED, this.keepDays, this.cutoffMillis, this.startedAtMs, l, l, this.actionsDeleted, this.snapshotsByAgeDeleted, this.orphanSnapshotsDeleted, this.batchSize, this.maintenanceMode, this.startedBy, this.lastMessage, string);
    }

    public static enum Stage {
        IDLE,
        RESETTING_SCHEMA,
        DELETING_ACTIONS,
        DELETING_SNAPSHOTS_BY_AGE,
        DELETING_ORPHAN_SNAPSHOTS,
        OPTIMIZING_ACTIONS,
        OPTIMIZING_SNAPSHOTS,
        DONE,
        CANCELLED,
        FAILED;

    }
}

