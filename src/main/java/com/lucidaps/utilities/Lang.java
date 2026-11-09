/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 *  org.bukkit.configuration.file.FileConfiguration
 */
package com.lucidaps.utilities;

import com.lucidaps.ProtectCore;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class Lang {
    private final ProtectCore plugin;

    public Lang(ProtectCore protectCore) {
        this.plugin = protectCore;
    }

    public String get(String string, Map<String, String> map) {
        FileConfiguration fileConfiguration = this.plugin.getConfig();
        String string2 = fileConfiguration.getString(string, "");
        for (Map.Entry<String, String> entry : map.entrySet()) {
            string2 = string2.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes((char)'&', (String)string2);
    }
}

