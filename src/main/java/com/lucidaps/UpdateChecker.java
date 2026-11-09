/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.lucidaps;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class UpdateChecker {
    private final JavaPlugin plugin;
    private final int resourceId;

    public UpdateChecker(JavaPlugin javaPlugin, int n) {
        this.plugin = javaPlugin;
        this.resourceId = n;
    }

    public void getVersion(Consumer<String> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously((Plugin)this.plugin, () -> {
            String string = "https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceId;
            try {
                URLConnection uRLConnection = new URL(string).openConnection();
                uRLConnection.setConnectTimeout(5000);
                uRLConnection.setReadTimeout(5000);
                try (InputStream inputStream = uRLConnection.getInputStream();
                     Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8);){
                    if (scanner.hasNext()) {
                        String string2 = scanner.next().trim();
                        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> consumer.accept(string2));
                    }
                }
            }
            catch (IOException iOException) {
                this.plugin.getLogger().warning("Unable to check for updates: " + iOException.getMessage());
            }
        });
    }
}

