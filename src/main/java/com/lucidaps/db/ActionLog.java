/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.annotations.SerializedName
 *  javax.annotation.Nullable
 */
package com.lucidaps.db;

import com.google.gson.annotations.SerializedName;
import com.lucidaps.db.EditorItemSummary;
import java.sql.Timestamp;
import java.util.UUID;
import javax.annotation.Nullable;

public class ActionLog {
    private final int id;
    private final UUID playerUUID;
    private final String playerName;
    private final String action;
    private final String details;
    private final String metadata;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final Timestamp timestamp;
    private final Timestamp eventTimestamp;
    @Nullable
    private final Integer snapshotId;
    @Nullable
    private final byte[] snapshotBytes;
    @SerializedName(value="rolled_back")
    private final boolean rolledBack;
    @Nullable
    private final Timestamp rolledBackTimestamp;
    @Nullable
    private final UUID rolledBackBy;
    @SerializedName(value="has_meta")
    private int hasMeta;
    @Nullable
    private String material;
    @Nullable
    private Integer amount;

    public ActionLog(int n, UUID uUID, String string, String string2, String string3, String string4, String string5, int n2, int n3, int n4, Timestamp timestamp, @Nullable Timestamp timestamp2, @Nullable Integer n5, @Nullable byte[] byArray) {
        this(n, uUID, string, string2, string3, string4, string5, n2, n3, n4, timestamp, timestamp2, n5, byArray, false, null, null);
    }

    public ActionLog(int n, UUID uUID, String string, String string2, String string3, String string4, String string5, int n2, int n3, int n4, Timestamp timestamp, @Nullable Timestamp timestamp2, @Nullable Integer n5, @Nullable byte[] byArray, boolean bl) {
        this(n, uUID, string, string2, string3, string4, string5, n2, n3, n4, timestamp, timestamp2, n5, byArray, bl, null, null);
    }

    public ActionLog(int n, UUID uUID, String string, String string2, String string3, String string4, String string5, int n2, int n3, int n4, Timestamp timestamp, @Nullable Timestamp timestamp2, @Nullable Integer n5, @Nullable byte[] byArray, boolean bl, @Nullable Timestamp timestamp3, @Nullable UUID uUID2) {
        this.id = n;
        this.playerUUID = uUID;
        this.playerName = string;
        this.action = string2;
        this.details = string3;
        this.metadata = string4;
        this.world = string5;
        this.x = n2;
        this.y = n3;
        this.z = n4;
        this.timestamp = timestamp;
        this.eventTimestamp = timestamp2;
        this.snapshotId = n5;
        this.snapshotBytes = byArray;
        this.rolledBack = bl;
        this.rolledBackTimestamp = timestamp3;
        this.rolledBackBy = uUID2;
    }

    public int getId() {
        return this.id;
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public String getAction() {
        return this.action;
    }

    public String getDetails() {
        return this.details;
    }

    public String getMetadata() {
        return this.metadata;
    }

    public String getWorld() {
        return this.world;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public Timestamp getTimestamp() {
        return this.timestamp;
    }

    public Timestamp getEventTimestamp() {
        return this.eventTimestamp != null ? this.eventTimestamp : this.timestamp;
    }

    @Nullable
    public Integer getSnapshotId() {
        return this.snapshotId;
    }

    @Nullable
    public byte[] getSnapshotBytes() {
        return this.snapshotBytes;
    }

    public boolean isRolledBack() {
        return this.rolledBack;
    }

    @Nullable
    public Timestamp getRolledBackTimestamp() {
        return this.rolledBackTimestamp;
    }

    @Nullable
    public UUID getRolledBackBy() {
        return this.rolledBackBy;
    }

    public int getHasMeta() {
        return this.hasMeta;
    }

    public ActionLog setHasMeta(int n) {
        this.hasMeta = n;
        return this;
    }

    @Nullable
    public String getMaterial() {
        return this.material;
    }

    @Nullable
    public Integer getAmount() {
        return this.amount;
    }

    ActionLog setItemSummary(EditorItemSummary editorItemSummary) {
        this.material = editorItemSummary.material();
        this.amount = editorItemSummary.amount();
        this.hasMeta = editorItemSummary.hasExtra() ? 1 : 0;
        return this;
    }
}

