/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nullable
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.entity.Player
 */
package com.lucidaps.commands.subcommands.rollback;

import com.lucidaps.ProtectCore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class RollbackModel {
    public static final List<String> TIME_SNIPPETS = List.of("5m", "10m", "1h", "6h", "1d");
    public static final List<String> RADIUS_SNIPPETS = List.of("5", "10", "25", "50", "global");

    public record ParsedArgs(long periodMillis, int radius, @Nullable UUID targetUuid, @Nullable String actionFilter, @Nullable String itemFilter, Set<String> includeMaterials, Set<String> excludeMaterials, List<String> invalidInclude, List<String> invalidExclude, int originX, int originY, int originZ, String worldName) {
        public static ParsedArgs parse(Player player, ProtectCore protectCore, String[] stringArray) {
            int n;
            String string = null;
            String string2 = null;
            String string3 = null;
            String string4 = null;
            String string5 = null;
            LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
            LinkedHashSet<String> linkedHashSet2 = new LinkedHashSet<String>();
            ArrayList<String> arrayList = new ArrayList<String>();
            ArrayList<String> arrayList2 = new ArrayList<String>();
            for (int i = 1; i < stringArray.length; ++i) {
                String string6 = stringArray[i];
                if (string6.equalsIgnoreCase("time:")) {
                    if (i + 1 >= stringArray.length) {
                        throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.usage", Map.of()));
                    }
                    string = stringArray[++i].trim();
                    continue;
                }
                if (string6.toLowerCase(Locale.ROOT).startsWith("time:")) {
                    string = string6.substring(5).trim();
                    continue;
                }
                if (string6.equalsIgnoreCase("radius:")) {
                    if (i + 1 >= stringArray.length) {
                        throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.usage", Map.of()));
                    }
                    string2 = stringArray[++i].trim();
                    continue;
                }
                if (string6.toLowerCase(Locale.ROOT).startsWith("radius:")) {
                    string2 = string6.substring(7).trim();
                    continue;
                }
                if (string6.equalsIgnoreCase("user:")) {
                    if (i + 1 >= stringArray.length) {
                        throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.usage", Map.of()));
                    }
                    string3 = stringArray[++i].trim();
                    continue;
                }
                if (string6.toLowerCase(Locale.ROOT).startsWith("user:")) {
                    string3 = string6.substring(5).trim();
                    continue;
                }
                if (string6.equalsIgnoreCase("action:")) {
                    if (i + 1 >= stringArray.length) {
                        throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.usage", Map.of()));
                    }
                    string4 = stringArray[++i].trim().toUpperCase(Locale.ROOT);
                    continue;
                }
                if (string6.toLowerCase(Locale.ROOT).startsWith("action:")) {
                    string4 = string6.substring(7).trim().toUpperCase(Locale.ROOT);
                    continue;
                }
                if (string6.equalsIgnoreCase("item:")) {
                    if (i + 1 >= stringArray.length) {
                        throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.usage", Map.of()));
                    }
                    string5 = stringArray[++i].trim().toLowerCase(Locale.ROOT);
                    continue;
                }
                if (string6.toLowerCase(Locale.ROOT).startsWith("item:")) {
                    string5 = string6.substring(5).trim().toLowerCase(Locale.ROOT);
                    continue;
                }
                if (string6.equalsIgnoreCase("include:")) {
                    if (i + 1 >= stringArray.length) {
                        throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.usage", Map.of()));
                    }
                    ParsedArgs.parseMaterialCSV(stringArray[++i], linkedHashSet, arrayList);
                    continue;
                }
                if (string6.toLowerCase(Locale.ROOT).startsWith("include:")) {
                    ParsedArgs.parseMaterialCSV(string6.substring(8), linkedHashSet, arrayList);
                    continue;
                }
                if (string6.equalsIgnoreCase("exclude:")) {
                    if (i + 1 >= stringArray.length) {
                        throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.usage", Map.of()));
                    }
                    ParsedArgs.parseMaterialCSV(stringArray[++i], linkedHashSet2, arrayList2);
                    continue;
                }
                if (string6.toLowerCase(Locale.ROOT).startsWith("exclude:")) {
                    ParsedArgs.parseMaterialCSV(string6.substring(8), linkedHashSet2, arrayList2);
                    continue;
                }
                if (string3 != null) continue;
                string3 = string6;
            }
            if (string == null || string2 == null) {
                throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.usage", Map.of()));
            }
            long l = ParsedArgs.parseTimeToMillis(string);
            if (l <= 0L) {
                throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.invalidTime", Map.of()));
            }
            long l2 = protectCore.getConfig().getLong("rollback.maxDays", 50L);
            if (l > l2 * 86400000L) {
                throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.daysExceeded", Map.of("max", String.valueOf(l2))));
            }
            int n2 = protectCore.getConfig().getInt("rollback.maxRadius", 50);
            boolean bl = player.hasPermission("protectcore.rollback.bypass");
            if ("global".equalsIgnoreCase(string2)) {
                if (!bl) {
                    throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.radiusExceeded", Map.of("max", String.valueOf(n2))));
                }
                n = -1;
            } else {
                try {
                    n = Integer.parseInt(string2);
                }
                catch (NumberFormatException numberFormatException) {
                    throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.invalidRadius", Map.of()));
                }
                if (n < 0) {
                    throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.invalidRadius", Map.of()));
                }
                if (n > n2 && !bl) {
                    throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.radiusExceeded", Map.of("max", String.valueOf(n2))));
                }
            }
            UUID uUID = null;
            if (string3 != null) {
                OfflinePlayer offlinePlayer;
                UUID uUID2 = null;
                try {
                    uUID2 = UUID.fromString(string3);
                }
                catch (IllegalArgumentException illegalArgumentException) {
                    // empty catch block
                }
                Object object = offlinePlayer = uUID2 != null ? Bukkit.getOfflinePlayer((UUID)uUID2) : Bukkit.getPlayerExact((String)string3);
                if (offlinePlayer == null) {
                    offlinePlayer = Bukkit.getOfflinePlayer((String)string3);
                }
                if (offlinePlayer == null || !offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
                    throw new IllegalArgumentException(protectCore.getLang().get("messages.rollback.invalidPlayer", Map.of("player", string3)));
                }
                uUID = offlinePlayer.getUniqueId();
            }
            return new ParsedArgs(l, n, uUID, string4, string5, linkedHashSet, linkedHashSet2, arrayList, arrayList2, player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ(), player.getWorld().getName());
        }

        @Nullable
        public static Material matchMatCompat(String string, boolean bl) {
            Material material = Material.matchMaterial((String)string);
            if (material == null && string.startsWith("minecraft:")) {
                material = Material.matchMaterial((String)string.substring(10));
            }
            if (bl && material == null) {
                material = Material.matchMaterial((String)string.toUpperCase(Locale.ROOT));
            }
            return material;
        }

        private static void parseMaterialCSV(String string, Set<String> set, List<String> list) {
            if (string == null) {
                return;
            }
            for (String string2 : string.split(",")) {
                String string3 = string2.trim();
                if (string3.isEmpty()) continue;
                Material material = ParsedArgs.matchMatCompat(string3, false);
                if (material != null) {
                    set.add(material.name().toLowerCase(Locale.ROOT));
                    continue;
                }
                list.add(string3);
            }
        }

        public static String formatMillis(long l) {
            long l2 = l / 1000L;
            if (l2 == 0L && l > 0L) {
                return "<1s";
            }
            if (l2 == 0L) {
                return "0s";
            }
            long l3 = l2 / 86400L;
            long l4 = l2 % 86400L / 3600L;
            long l5 = l2 % 3600L / 60L;
            long l6 = l2 % 60L;
            StringBuilder stringBuilder = new StringBuilder();
            if (l3 > 0L) {
                stringBuilder.append(l3).append("d");
            }
            if (l4 > 0L) {
                stringBuilder.append(l4).append("h");
            }
            if (l5 > 0L) {
                stringBuilder.append(l5).append("m");
            }
            if (l6 > 0L || stringBuilder.length() == 0) {
                stringBuilder.append(l6).append("s");
            }
            return stringBuilder.toString();
        }

        private static long parseTimeToMillis(String string) {
            if ((string = string.trim().toLowerCase(Locale.ROOT)).endsWith("ms")) {
                return Long.parseLong(string.substring(0, string.length() - 2));
            }
            if (string.endsWith("s")) {
                return Long.parseLong(string.substring(0, string.length() - 1)) * 1000L;
            }
            if (string.endsWith("m")) {
                return Long.parseLong(string.substring(0, string.length() - 1)) * 60L * 1000L;
            }
            if (string.endsWith("h")) {
                return Long.parseLong(string.substring(0, string.length() - 1)) * 3600L * 1000L;
            }
            if (string.endsWith("d")) {
                return Long.parseLong(string.substring(0, string.length() - 1)) * 86400L * 1000L;
            }
            return Long.parseLong(string) * 1000L;
        }
    }

    public static enum Phase {
        BLOCKS,
        ENTITIES,
        ENTITY_ITEMS,
        CONTAINERS;

    }
}

