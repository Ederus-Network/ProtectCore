/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package com.lucidaps.listeners.util;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Material;

public class DropKey {
    private final UUID playerUUID;
    private final Material material;
    private final String metadata;

    public DropKey(UUID uUID, Material material, String string) {
        this.playerUUID = uUID;
        this.material = material;
        this.metadata = string;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DropKey)) {
            return false;
        }
        DropKey dropKey = (DropKey)object;
        return this.playerUUID.equals(dropKey.playerUUID) && this.material == dropKey.material && Objects.equals(this.metadata, dropKey.metadata);
    }

    public int hashCode() {
        return Objects.hash(this.playerUUID, this.material, this.metadata);
    }
}

