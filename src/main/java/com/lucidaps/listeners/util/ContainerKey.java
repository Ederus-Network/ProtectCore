/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 */
package com.lucidaps.listeners.util;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;

public class ContainerKey {
    private final UUID playerId;
    private final Location location;

    public ContainerKey(UUID uUID, Location location) {
        this.playerId = uUID;
        this.location = location;
    }

    public Location getLocation() {
        return this.location;
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || this.getClass() != object.getClass()) {
            return false;
        }
        ContainerKey containerKey = (ContainerKey)object;
        return this.playerId.equals(containerKey.playerId) && this.location.equals((Object)containerKey.location);
    }

    public int hashCode() {
        return Objects.hash(this.playerId, this.location);
    }
}

