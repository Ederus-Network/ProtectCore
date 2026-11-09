/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package com.lucidaps.commands.subcommands;

import com.lucidaps.ProtectCore;
import com.lucidaps.commands.SubCommand;
import com.lucidaps.commands.subcommands.rollback.RollbackEngine;
import com.lucidaps.commands.subcommands.rollback.RollbackModel;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.logging.ActionType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RollbackSubCommand
implements SubCommand {
    private final ProtectCore plugin;
    private final RollbackEngine rollbackEngine;
    private final Map<UUID, PendingGlobal> pendingGlobal = new ConcurrentHashMap<UUID, PendingGlobal>();

    public RollbackSubCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.rollbackEngine = new RollbackEngine(protectCore, databaseMariadb);
    }

    @Override
    public String getName() {
        return "rollback";
    }

    private static String canonicalKey(String string) {
        String string2;
        return switch (string2 = string.toLowerCase(Locale.ROOT)) {
            case "u" -> "user";
            case "r" -> "radius";
            case "t" -> "time";
            case "a" -> "action";
            case "i" -> "include";
            case "e" -> "exclude";
            case "it" -> "item";
            default -> string2;
        };
    }

    private static String normalizeAliasToken(String string) {
        String string2;
        String string3 = string.toLowerCase(Locale.ROOT);
        int n = string3.indexOf(58);
        if (n <= 0) {
            return string;
        }
        String string4 = string3.substring(0, n);
        String string5 = string.substring(n + 1);
        switch (string4) {
            case "u": {
                String string6 = "user";
                break;
            }
            case "r": {
                String string6 = "radius";
                break;
            }
            case "t": {
                String string6 = "time";
                break;
            }
            case "a": {
                String string6 = "action";
                break;
            }
            case "i": {
                String string6 = "include";
                break;
            }
            case "e": {
                String string6 = "exclude";
                break;
            }
            case "it": {
                String string6 = "item";
                break;
            }
            default: {
                String string6 = string2 = string4;
            }
        }
        if (string2.equals(string4)) {
            return string;
        }
        return string2 + ":" + string5;
    }

    @Override
    public boolean execute(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.onlyPlayer", Map.of()));
            return true;
        }
        Player player = (Player)commandSender;
        if (!player.hasPermission("protectcore.rollback")) {
            player.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        try {
            RollbackModel.ParsedArgs parsedArgs;
            String[] stringArray2 = new String[stringArray.length];
            for (int i = 0; i < stringArray.length; ++i) {
                stringArray2[i] = RollbackSubCommand.normalizeAliasToken(stringArray[i]);
            }
            try {
                parsedArgs = RollbackModel.ParsedArgs.parse(player, this.plugin, stringArray2);
            }
            catch (IllegalArgumentException illegalArgumentException) {
                player.sendMessage(String.valueOf(ChatColor.RED) + illegalArgumentException.getMessage());
                return true;
            }
            if (parsedArgs.radius() < 0) {
                int n = this.plugin.getConfig().getInt("rollback.global.confirmWindowSeconds", 15);
                UUID uUID = player.getUniqueId();
                long l = System.currentTimeMillis();
                PendingGlobal pendingGlobal = this.pendingGlobal.get(uUID);
                if (pendingGlobal == null || pendingGlobal.isExpired() || !pendingGlobal.matches(parsedArgs)) {
                    this.pendingGlobal.put(uUID, new PendingGlobal(parsedArgs, l + (long)n * 1000L));
                    player.sendMessage(this.plugin.getLang().get("messages.rollback.globalConfirm", Map.of("time", RollbackModel.ParsedArgs.formatMillis(parsedArgs.periodMillis()), "seconds", String.valueOf(n))));
                    return true;
                }
                this.pendingGlobal.remove(uUID);
            }
            this.rollbackEngine.startRollback((CommandSender)player, parsedArgs);
        }
        catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Unexpected error in rollback command: " + exception.getMessage(), exception);
            commandSender.sendMessage(String.valueOf(ChatColor.RED) + this.plugin.getLang().get("messages.rollback.errorInitiating", Map.of()));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        String string2;
        String string3;
        String string4;
        ArrayList<String> arrayList = new ArrayList<String>();
        if (!commandSender.hasPermission("protectcore.rollback")) {
            return arrayList;
        }
        List<String> list = List.of("user", "radius", "time", "action", "item", "include", "exclude");
        List<String> list2 = List.of("user:", "radius:", "time:", "action:", "item:", "include:", "exclude:");
        int n = stringArray.length;
        String string5 = n == 0 ? "" : stringArray[n - 1];
        String string6 = string5.toLowerCase(Locale.ROOT);
        if (n <= 1) {
            this.offerPlayerNames(string6, arrayList, "");
            for (String string7 : list2) {
                if (!string7.startsWith(string6)) continue;
                arrayList.add(string7);
            }
            return RollbackSubCommand.dedupe(arrayList);
        }
        HashSet<String> hashSet = new HashSet<String>();
        for (int i = 1; i < n - 1; ++i) {
            string4 = stringArray[i].toLowerCase(Locale.ROOT);
            int n2 = string4.indexOf(58);
            if (n2 < 0) continue;
            string3 = string4.substring(0, n2);
            string2 = RollbackSubCommand.canonicalKey(string3);
            hashSet.add(string2);
        }
        if (string6.contains(":")) {
            String[] stringArray2 = string6.split(":", 2);
            string4 = stringArray2[0];
            String string8 = RollbackSubCommand.canonicalKey(string4);
            string3 = stringArray2.length > 1 ? stringArray2[1] : "";
            switch (string8) {
                case "user": {
                    this.offerPlayerNames(string3, arrayList, "user:");
                    break;
                }
                case "action": {
                    this.suggestActions(string3, arrayList, "action:");
                    break;
                }
                case "item": {
                    this.suggestMaterials(string3, arrayList, "item:");
                    break;
                }
                case "time": {
                    this.suggestSnippets(string3, arrayList, "time:", RollbackModel.TIME_SNIPPETS);
                    break;
                }
                case "radius": {
                    this.suggestSnippets(string3, arrayList, "radius:", RollbackModel.RADIUS_SNIPPETS);
                    break;
                }
                case "include": {
                    this.suggestMaterialsCSV(string3, arrayList, "include:");
                    break;
                }
                case "exclude": {
                    this.suggestMaterialsCSV(string3, arrayList, "exclude:");
                }
            }
            return RollbackSubCommand.dedupe(arrayList);
        }
        String string9 = stringArray[n - 2].toLowerCase(Locale.ROOT);
        string4 = string9;
        if (string4.endsWith(":")) {
            string4 = string4.substring(0, string4.length() - 1);
        }
        string4 = RollbackSubCommand.canonicalKey(string4);
        switch (string4) {
            case "user": {
                this.offerPlayerNames(string6, arrayList, "");
                return RollbackSubCommand.dedupe(arrayList);
            }
            case "action": {
                this.suggestActions(string6, arrayList, "");
                return RollbackSubCommand.dedupe(arrayList);
            }
            case "item": {
                this.suggestMaterials(string6, arrayList, "");
                return RollbackSubCommand.dedupe(arrayList);
            }
            case "time": {
                this.suggestSnippets(string6, arrayList, "", RollbackModel.TIME_SNIPPETS);
                return RollbackSubCommand.dedupe(arrayList);
            }
            case "radius": {
                this.suggestSnippets(string6, arrayList, "", RollbackModel.RADIUS_SNIPPETS);
                return RollbackSubCommand.dedupe(arrayList);
            }
            case "include": {
                this.suggestMaterialsCSV(string6, arrayList, "");
                return RollbackSubCommand.dedupe(arrayList);
            }
            case "exclude": {
                this.suggestMaterialsCSV(string6, arrayList, "");
                return RollbackSubCommand.dedupe(arrayList);
            }
        }
        for (String string10 : list2) {
            string2 = string10.substring(0, string10.length() - 1);
            String string11 = RollbackSubCommand.canonicalKey(string2);
            if (hashSet.contains(string11) || !string10.startsWith(string6)) continue;
            arrayList.add(string10);
        }
        if (!hashSet.contains("user")) {
            this.offerPlayerNames(string6, arrayList, "");
        }
        return RollbackSubCommand.dedupe(arrayList);
    }

    private static List<String> dedupe(List<String> list) {
        return new ArrayList<String>(new LinkedHashSet<String>(list));
    }

    private void suggestMaterialsCSV(String string, List<String> list, String string2) {
        String string3 = string == null ? "" : string;
        int n = string3.lastIndexOf(44);
        String string4 = n >= 0 ? string3.substring(0, n + 1) : "";
        String string5 = n >= 0 ? string3.substring(n + 1) : string3;
        String string6 = string5.toLowerCase(Locale.ROOT).trim();
        int n2 = 20;
        int n3 = 0;
        for (Material material : Material.values()) {
            String string7;
            if (material.isLegacy() || !(string7 = material.name().toLowerCase(Locale.ROOT)).startsWith(string6)) continue;
            list.add(string2 + string4 + string7);
            if (++n3 >= n2) break;
        }
    }

    private void offerPlayerNames(String string, List<String> list, String string2) {
        String string3 = string.toLowerCase(Locale.ROOT);
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(string3)) {
                list.add(string2 + player.getName());
            }
        });
    }

    private void suggestActions(String string, List<String> list, String string2) {
        String string4 = string.toLowerCase(Locale.ROOT);
        ActionType.visibleNames().forEach(string3 -> {
            if (string3.toLowerCase(Locale.ROOT).startsWith(string4)) {
                list.add(string2 + string3.toLowerCase(Locale.ROOT));
            }
        });
    }

    private void suggestMaterials(String string, List<String> list, String string2) {
        String string3 = string.toLowerCase(Locale.ROOT);
        int n = 0;
        int n2 = 20;
        for (Material material : Material.values()) {
            String string4;
            if (material.isLegacy() || !(string4 = material.name().toLowerCase(Locale.ROOT)).startsWith(string3)) continue;
            list.add(string2 + string4);
            if (++n >= n2) break;
        }
    }

    private void suggestSnippets(String string, List<String> list, String string2, List<String> list2) {
        String string4 = string.toLowerCase(Locale.ROOT);
        list2.forEach(string3 -> {
            if (string3.toLowerCase(Locale.ROOT).startsWith(string4)) {
                list.add(string2 + string3);
            }
        });
    }

    private static final class PendingGlobal {
        final RollbackModel.ParsedArgs args;
        final long expiresAt;

        PendingGlobal(RollbackModel.ParsedArgs parsedArgs, long l) {
            this.args = parsedArgs;
            this.expiresAt = l;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > this.expiresAt;
        }

        boolean matches(RollbackModel.ParsedArgs parsedArgs) {
            return parsedArgs != null && parsedArgs.radius() < 0 && parsedArgs.periodMillis() == this.args.periodMillis() && Objects.equals(parsedArgs.targetUuid(), this.args.targetUuid()) && Objects.equals(parsedArgs.actionFilter(), this.args.actionFilter()) && Objects.equals(parsedArgs.itemFilter(), this.args.itemFilter()) && Objects.equals(parsedArgs.includeMaterials(), this.args.includeMaterials()) && Objects.equals(parsedArgs.excludeMaterials(), this.args.excludeMaterials());
        }
    }
}

