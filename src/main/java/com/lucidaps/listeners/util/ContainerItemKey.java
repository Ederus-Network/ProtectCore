/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 */
package com.lucidaps.listeners.util;

import java.util.Objects;
import org.bukkit.Material;

public class ContainerItemKey {
    private final Material material;
    private final String meta;

    public ContainerItemKey(Material material, String string) {
        this.material = material;
        this.meta = string == null ? "" : string;
    }

    public Material getMaterial() {
        return this.material;
    }

    public String getMeta() {
        return this.meta;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || this.getClass() != object.getClass()) {
            return false;
        }
        ContainerItemKey containerItemKey = (ContainerItemKey)object;
        return this.material == containerItemKey.material && this.meta.equals(containerItemKey.meta);
    }

    public int hashCode() {
        return Objects.hash(this.material, this.meta);
    }

    public String toString() {
        return this.material.toString() + (String)(this.meta.isEmpty() ? "" : "{" + this.meta + "}");
    }
}

