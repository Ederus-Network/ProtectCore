/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 */
package com.lucidaps.listeners.util;

import org.bukkit.Location;

public class DropAggregateData {
    private int count;
    private final Location location;

    public DropAggregateData(int n, Location location) {
        this.count = n;
        this.location = location;
    }

    public int getCount() {
        return this.count;
    }

    public DropAggregateData incrementCount(int n) {
        this.count += n;
        return this;
    }

    public Location getLocation() {
        return this.location;
    }
}

