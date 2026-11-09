/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.lucidaps.utilities;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrettyConsole {
    private static final int WIDTH = 64;

    private PrettyConsole() {
    }

    public static void banner(JavaPlugin javaPlugin) {
        PrettyConsole.rule(javaPlugin);
        PrettyConsole.center(javaPlugin, "&d&lProtect&b&lCore &7v" + javaPlugin.getDescription().getVersion());
        PrettyConsole.center(javaPlugin, "&7by LucidAPS");
        PrettyConsole.send(javaPlugin, "&8" + PrettyConsole.repeat("\u2500", 64));
        PrettyConsole.kv(javaPlugin, "Server", Bukkit.getName() + " &8(" + Bukkit.getVersion() + "&8)");
        PrettyConsole.kv(javaPlugin, "Java", System.getProperty("java.version"));
        PrettyConsole.rule(javaPlugin);
    }

    public static long begin(JavaPlugin javaPlugin, String string) {
        PrettyConsole.send(javaPlugin, "&7\u27a4 &f" + string + "&7\u2026");
        return System.nanoTime();
    }

    public static void ok(JavaPlugin javaPlugin, long l) {
        PrettyConsole.ok(javaPlugin, l, null);
    }

    public static void ok(JavaPlugin javaPlugin, long l, String string) {
        long l2 = (System.nanoTime() - l) / 1000000L;
        PrettyConsole.send(javaPlugin, "&a  \u2714 &7Done &8(" + l2 + "ms)&7" + (String)(string != null && !string.isBlank() ? " \u2014 " + string : ""));
    }

    public static void fail(JavaPlugin javaPlugin, long l, Throwable throwable) {
        long l2 = (System.nanoTime() - l) / 1000000L;
        PrettyConsole.send(javaPlugin, "&c  \u2716 &7Failed &8(" + l2 + "ms)&7 \u2014 &c" + (throwable == null ? "Unknown error" : throwable.getMessage()));
    }

    public static void kv(JavaPlugin javaPlugin, String string, String string2) {
        String string3 = "&8" + PrettyConsole.padRight(string + ":", 15);
        PrettyConsole.send(javaPlugin, string3 + " &f" + string2);
    }

    public static void rule(JavaPlugin javaPlugin) {
        PrettyConsole.send(javaPlugin, "&d" + PrettyConsole.repeat("\u2550", 64));
    }

    public static void footer(JavaPlugin javaPlugin, long l) {
        long l2 = (System.nanoTime() - l) / 1000000L;
        PrettyConsole.rule(javaPlugin);
        PrettyConsole.center(javaPlugin, "&a&l\u2714 ProtectCore ready&7 in &a" + l2 + "ms");
        PrettyConsole.rule(javaPlugin);
    }

    private static void center(JavaPlugin javaPlugin, String string) {
        String string2 = PrettyConsole.stripColors(string);
        int n = Math.max(0, (64 - string2.length()) / 2);
        PrettyConsole.send(javaPlugin, PrettyConsole.repeat(" ", n) + string);
    }

    private static String stripColors(String string) {
        return string.replaceAll("(?i)&[0-9A-FK-OR]", "").replace("\u00a7", "");
    }

    private static String padRight(String string, int n) {
        if (string.length() >= n) {
            return string;
        }
        return string + PrettyConsole.repeat(" ", n - string.length());
    }

    private static String repeat(String string, int n) {
        return string.repeat(Math.max(0, n));
    }

    private static String color(String string) {
        return ChatColor.translateAlternateColorCodes((char)'&', (String)string);
    }

    private static void send(JavaPlugin javaPlugin, String string) {
        Bukkit.getConsoleSender().sendMessage(PrettyConsole.color(string));
    }
}

