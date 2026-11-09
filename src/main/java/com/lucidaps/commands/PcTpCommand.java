/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 */
package com.lucidaps.commands;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class PcTpCommand
implements CommandExecutor,
TabCompleter {
    private static final long TOKEN_TTL_MS = 60000L;
    private static final Map<String, Long> validTokens = new ConcurrentHashMap<String, Long>();

    public static void registerToken(String string) {
        PcTpCommand.registerToken(string, System.currentTimeMillis());
    }

    static void registerToken(String string, long l) {
        validTokens.put(string, l);
    }

    static boolean consumeToken(String string, long l) {
        Long l2 = string == null ? null : validTokens.remove(string);
        return l2 != null && l - l2 <= 60000L;
    }

    public static IssuedCommand issueCommand(String string, int n, int n2, int n3) {
        if (string == null || !string.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid world key");
        }
        String string2 = UUID.randomUUID().toString();
        PcTpCommand.registerToken(string2);
        return new IssuedCommand("/pc_tp " + string2 + " " + string + " " + n + " " + n2 + " " + n3, 60L);
    }

    public static void purgeExpiredTokens(long l) {
        validTokens.entrySet().removeIf(entry -> l - (Long)entry.getValue() > 60000L);
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        double d;
        double d2;
        double d3;
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player)commandSender;
        if (!player.hasPermission("protectcore.hidden.tp")) {
            return true;
        }
        if (stringArray.length < 5) {
            player.sendMessage("This command cannot be used directly.");
            return true;
        }
        String string2 = stringArray[0];
        if (!PcTpCommand.consumeToken(string2, System.currentTimeMillis())) {
            player.sendMessage("This command cannot be used directly.");
            return true;
        }
        String string3 = stringArray[1];
        try {
            d3 = Double.parseDouble(stringArray[2]);
            d2 = Double.parseDouble(stringArray[3]);
            d = Double.parseDouble(stringArray[4]);
        }
        catch (NumberFormatException numberFormatException) {
            player.sendMessage("Invalid coordinates.");
            return true;
        }
        World world = PcTpCommand.resolveWorld(string3);
        if (world == null) {
            player.sendMessage("World not found: " + string3);
            return true;
        }
        Location location = new Location(world, d3, d2, d);
        player.teleport(location);
        return true;
    }

    public static World resolveWorld(String string) {
        World world = Bukkit.getWorld((String)string);
        if (world != null) {
            return world;
        }
        return PcTpCommand.resolveWorld(string, Bukkit.getWorlds());
    }

    static World resolveWorld(String string, Iterable<World> iterable) {
        for (World world : iterable) {
            if (!world.getName().equalsIgnoreCase(string) && !world.getKey().toString().equalsIgnoreCase(string)) continue;
            return world;
        }
        return null;
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        return Collections.emptyList();
    }

    public record IssuedCommand(String command, long expiresInSeconds) {
    }
}

