/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.Material
 */
package com.lucidaps.listeners.util;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Material;

public class HopperTransferKey {
    private final Location location;
    private final String holderType;
    private final Material material;
    private final String metadata;

    public HopperTransferKey(Location location, String string, Material material, String string2) {
        this.location = location.toBlockLocation();
        this.holderType = string;
        this.material = material;
        this.metadata = string2;
    }

    public Location getLocation() {
        return this.location;
    }

    public String getHolderType() {
        return this.holderType;
    }

    public Material getMaterial() {
        return this.material;
    }

    public String getMetadata() {
        return this.metadata;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof HopperTransferKey)) {
            return false;
        }
        HopperTransferKey hopperTransferKey = (HopperTransferKey)object;
        return this.location.equals((Object)hopperTransferKey.location) && this.holderType.equals(hopperTransferKey.holderType) && this.material == hopperTransferKey.material && Objects.equals(this.metadata, hopperTransferKey.metadata);
    }

    public int hashCode() {
        return Objects.hash(this.location, this.holderType, this.material, this.metadata);
    }
}

