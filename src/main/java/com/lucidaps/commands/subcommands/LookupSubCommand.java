/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.ComponentLike
 *  net.kyori.adventure.text.JoinConfiguration
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.TextDecoration
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package com.lucidaps.commands.subcommands;

import com.lucidaps.ProtectCore;
import com.lucidaps.commands.PoCommand;
import com.lucidaps.commands.SubCommand;
import com.lucidaps.db.ActionLog;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.logging.ActionType;
import com.lucidaps.utilities.TimeUtil;
import java.lang.invoke.CallSite;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LookupSubCommand
implements SubCommand {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private final int PAGE_SIZE;
    private static final List<String> TIME_SNIPPETS = List.of("5m", "10m", "30m", "1h", "6h", "1d");
    private static final List<String> RADIUS_SNIPPETS = List.of("5", "10", "20", "50");
    private final int defaultRadius;

    public LookupSubCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
        this.defaultRadius = Math.max(1, protectCore.getConfig().getInt("lookup.defaultRadius", 20));
        this.PAGE_SIZE = Math.max(1, protectCore.getConfig().getInt("lookup.pageSize", 4));
    }

    @Override
    public String getName() {
        return "lookup";
    }

    private static String canonicalKey(String string) {
        String string2;
        return switch (string2 = string.toLowerCase(Locale.ROOT)) {
            case "u" -> "user";
            case "r" -> "radius";
            case "t" -> "time";
            case "p" -> "page";
            case "a" -> "action";
            case "i" -> "include";
            case "e" -> "exclude";
            default -> string2;
        };
    }

    @Override
    public boolean execute(CommandSender commandSender, Command command, String string, String[] stringArray) {
        long l;
        Object object;
        if (!commandSender.hasPermission("protectcore.lookup")) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        String object2 = null;
        Integer n = null;
        String object3 = "1d";
        Integer n2 = 1;
        String string2 = null;
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        LinkedHashSet<String> linkedHashSet2 = new LinkedHashSet<String>();
        ArrayList<String> arrayList = new ArrayList<String>();
        ArrayList<String> arrayList2 = new ArrayList<String>();
        LinkedHashSet<String> linkedHashSet3 = linkedHashSet;
        LinkedHashSet<String> linkedHashSet4 = linkedHashSet2;
        block22: for (int i = 1; i < stringArray.length; ++i) {
            String string3 = stringArray[i];
            String string4 = string3.toLowerCase(Locale.ROOT);
            int n3 = string4.indexOf(58);
            if (n3 >= 0) {
                String string5 = string4.substring(0, n3);
                String string6 = LookupSubCommand.canonicalKey(string5);
                object = string3.substring(n3 + 1);
                if (((String)object).isEmpty()) {
                    if (i + 1 >= stringArray.length) {
                        commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Missing value after " + string6 + ":");
                        return true;
                    }
                    object = stringArray[++i];
                }
                try {
                    switch (string6) {
                        case "user": {
                            object2 = (String)object;
                            break;
                        }
                        case "radius": {
                            n = Integer.parseInt((String)object);
                            break;
                        }
                        case "time": {
                            object3 = (String)object;
                            break;
                        }
                        case "page": {
                            n2 = Integer.parseInt((String)object);
                            break;
                        }
                        case "action": {
                            String raw = ((String)object).toUpperCase(Locale.ROOT);
                            if (raw.equals("CHAT") || raw.equals("MSG") || raw.equals("MESSAGE") || raw.equals("PLAYER_MESSAGE")) {
                                string2 = "PLAYER_MESSAGE";
                            } else if (raw.equals("CMD") || raw.equals("COMMAND") || raw.equals("PLAYER_COMMAND")) {
                                string2 = "PLAYER_COMMAND";
                            } else if (raw.equals("ALL")) {
                                string2 = "ALL";
                            } else {
                                string2 = raw;
                            }
                            break;
                        }
                        case "include": {
                            LookupSubCommand.parseMaterialCSV((String)object, linkedHashSet, arrayList);
                            break;
                        }
                        case "exclude": {
                            LookupSubCommand.parseMaterialCSV((String)object, linkedHashSet2, arrayList2);
                            break;
                        }
                        default: {
                            if (object2 != null) continue block22;
                            object2 = string3;
                            break;
                        }
                    }
                    continue;
                }
                catch (NumberFormatException numberFormatException) {
                    if ("radius".equals(string6)) {
                        commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Invalid radius: " + (String)object);
                    } else if ("page".equals(string6)) {
                        commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Invalid page: " + (String)object);
                    }
                    return true;
                }
            }
            if (object2 != null) continue;
            object2 = string3;
        }
        if (!arrayList.isEmpty() || !arrayList2.isEmpty()) {
            ArrayList<String> arrayList3 = new ArrayList<String>();
            if (!arrayList.isEmpty()) {
                arrayList3.add("include: " + String.join(", ", arrayList));
            }
            if (!arrayList2.isEmpty()) {
                arrayList3.add("exclude: " + String.join(", ", arrayList2));
            }
            commandSender.sendMessage(String.valueOf(ChatColor.YELLOW) + "Unknown material(s) ignored: " + String.join(" | ", arrayList3));
        }
        if (object2 == null && n == null) {
            if (!(commandSender instanceof Player)) {
                commandSender.sendMessage(String.valueOf(ChatColor.RED) + "From console, specify either user:<name> or radius:<blocks> for area lookup.");
                return true;
            }
            n = this.defaultRadius;
        }
        if ((l = TimeUtil.parseTimePeriod(object3)) <= 0L) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.lookup.invalidTime", Map.of("period", object3)));
            return true;
        }
        long l2 = System.currentTimeMillis() - l;
        long l3 = Long.MAX_VALUE;
        LinkedHashSet<String> disabledActions = new LinkedHashSet<String>();
        disabledActions.add("CONTAINER_SNAPSHOT");
        if (string2 == null || (!string2.equals("PLAYER_MESSAGE") && !string2.equals("ALL"))) {
            disabledActions.add("PLAYER_MESSAGE");
        }
        if (string2 == null || (!string2.equals("PLAYER_COMMAND") && !string2.equals("ALL"))) {
            disabledActions.add("PLAYER_COMMAND");
        }
        if ("ALL".equals(string2)) {
            string2 = null;
        }
        object = disabledActions;
        String string7 = object2;
        Integer n4 = n;
        String string8 = object3;
        int n5 = n2;
        String string9 = string2;
        boolean bl = !this.plugin.isWorldEditReportLogsEnabled();
        int n6 = Math.max(1, this.plugin.getConfig().getInt("lookup.windowPages", 10));
        Consumer<DatabaseMariadb.SearchResult> consumer = searchResult -> {
            List<ActionLog> list = searchResult.logs;
            if (list.isEmpty()) {
                commandSender.sendMessage(this.plugin.getLang().get("messages.lookup.noLogs", Map.of("user", string7 != null ? string7 : "anyone", "period", string8)));
                return;
            }
            TextComponent textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(this.plugin.getLang().get("messages.lookup.header", Map.of()));
            Set set3 = list.stream().map(ActionLog::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            this.databaseMariadb.fetchRolledBackFlags(set3, arg_0 -> LookupSubCommand.lambda$execute$0(list, searchResult, string7, string8, n5, n4, string9, linkedHashSet3, linkedHashSet4, commandSender, (Component)textComponent, arg_0));
        };
        Consumer<DatabaseMariadb.WindowedResult> consumer2 = windowedResult -> {
            List<ActionLog> list = windowedResult.logs;
            if (list.isEmpty()) {
                commandSender.sendMessage(this.plugin.getLang().get("messages.lookup.noLogs", Map.of("user", string7 != null ? string7 : "anyone", "period", string8)));
                return;
            }
            TextComponent textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(this.plugin.getLang().get("messages.lookup.header", Map.of()));
            Set set3 = list.stream().map(ActionLog::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            this.databaseMariadb.fetchRolledBackFlags(set3, arg_0 -> LookupSubCommand.lambda$execute$2(list, string7, string8, windowedResult, n4, string9, linkedHashSet3, linkedHashSet4, commandSender, (Component)textComponent, arg_0));
        };
        if (n4 != null) {
            if (!(commandSender instanceof Player)) {
                commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Radius lookup must be run by an in-game player.");
                return true;
            }
            Player player = (Player)commandSender;
            String string10 = null;
            if (string7 != null && !string7.isBlank()) {
                UUID uUID = this.resolvePlayerUUID(string7);
                if (uUID == null) {
                    commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Unknown player: " + string7);
                    return true;
                }
                string10 = uUID.toString();
            }
            this.databaseMariadb.searchLogsPagedByAreaAsync(player.getWorld().getName(), player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ(), n4, l2 / 1000L, l3, string10, null, null, null, null, null, string9, (Set<String>)object, false, bl, this.PAGE_SIZE, n5, linkedHashSet3, linkedHashSet4, consumer);
        } else {
            if (string7 == null || string7.isBlank()) {
                commandSender.sendMessage(String.valueOf(ChatColor.RED) + "From console, specify either user:<name> or radius:<blocks> for area lookup.");
                return true;
            }
            UUID uUID = this.resolvePlayerUUID(string7);
            if (uUID == null) {
                commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Unknown player: " + string7);
                return true;
            }
            this.databaseMariadb.searchLogsWindowedByUserAsync(uUID.toString(), l2 / 1000L, l3, null, null, null, null, null, string9, (Set<String>)object, false, bl, this.PAGE_SIZE, n5, n6, linkedHashSet3, linkedHashSet4, consumer2);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, Command command, String string2, String[] stringArray) {
        Object object;
        Object object2;
        String string3;
        ArrayList<String> arrayList = new ArrayList<String>();
        if (!commandSender.hasPermission("protectcore.lookup")) {
            return arrayList;
        }
        if (stringArray.length == 0 || stringArray.length == 1 && stringArray[0].isBlank()) {
            this.offerPlayerNames("", arrayList, "");
            return arrayList;
        }
        if (stringArray.length == 1) {
            String string4 = stringArray[0].toLowerCase(Locale.ROOT);
            this.offerPlayerNames(string4, arrayList, "");
            return arrayList;
        }
        HashSet<Object> hashSet = new HashSet<Object>();
        for (int i = 0; i < stringArray.length - 1; ++i) {
            string3 = stringArray[i].toLowerCase(Locale.ROOT);
            int n = string3.indexOf(58);
            if (n < 0) continue;
            object2 = string3.substring(0, n);
            object = LookupSubCommand.canonicalKey((String)object2);
            hashSet.add(object);
        }
        Consumer<String> consumer = s -> {
            String sub = s.substring(0, s.length() - 1);
            String canon = LookupSubCommand.canonicalKey(sub);
            if (!hashSet.contains(canon)) {
                arrayList.add(s);
            }
        };
        string3 = stringArray[stringArray.length - 1];
        String string5 = string3.toLowerCase(Locale.ROOT);
        if (string5.contains(":")) {
            String[] parts = string5.split(":", 2);
            String prefixKey = parts[0];
            String string6 = LookupSubCommand.canonicalKey(prefixKey);
            String string7 = parts.length == 2 ? parts[1] : "";
            switch (string6) {
                case "user": {
                    if (hashSet.contains("user")) break;
                    this.offerPlayerNames(string7, arrayList, "user:");
                    break;
                }
                case "action": {
                    if (hashSet.contains("action")) break;
                    this.suggestActions(string7, arrayList, "action:");
                    break;
                }
                case "time": {
                    this.suggestSnippets(string7, arrayList, "time:", TIME_SNIPPETS);
                    break;
                }
                case "radius": {
                    this.suggestSnippets(string7, arrayList, "radius:", RADIUS_SNIPPETS);
                    break;
                }
                case "include": {
                    this.suggestMaterialsCSV(string7, arrayList, "include:");
                    break;
                }
                case "exclude": {
                    this.suggestMaterialsCSV(string7, arrayList, "exclude:");
                }
            }
            return arrayList;
        }
        String prevArg = stringArray[stringArray.length - 2].toLowerCase(Locale.ROOT);
        if (prevArg.endsWith(":")) {
            prevArg = prevArg.substring(0, prevArg.length() - 1);
        }
        String canonicalPrev = LookupSubCommand.canonicalKey(prevArg);
        switch (canonicalPrev) {
            case "user": {
                this.offerPlayerNames(string5, arrayList, "");
                break;
            }
            case "action": {
                this.suggestActions(string5, arrayList, "");
                break;
            }
            case "time": {
                this.suggestSnippets(string5, arrayList, "", TIME_SNIPPETS);
                break;
            }
            case "radius": {
                this.suggestSnippets(string5, arrayList, "", RADIUS_SNIPPETS);
                break;
            }
            case "include": {
                this.suggestMaterialsCSV(string5, arrayList, "");
                break;
            }
            case "exclude": {
                this.suggestMaterialsCSV(string5, arrayList, "");
                break;
            }
            default: {
                for (String string8 : List.of("user:", "radius:", "time:", "page:", "action:", "include:", "exclude:")) {
                    if (!string8.startsWith(string5)) continue;
                    consumer.accept(string8);
                }
            }
        }
        return arrayList;
    }

    private static void parseMaterialCSV(String string, Set<String> set, List<String> list) {
        String[] stringArray;
        if (string == null) {
            return;
        }
        for (String string2 : stringArray = string.split(",")) {
            Material material;
            String string3 = string2.trim();
            if (string3.isEmpty()) continue;
            String string4 = string3;
            int n = string4.indexOf(58);
            if (n >= 0) {
                string4 = string4.substring(n + 1);
            }
            if ((material = Material.matchMaterial((String)string4, (boolean)false)) != null) {
                set.add(material.name().toLowerCase(Locale.ROOT));
                continue;
            }
            list.add(string3);
        }
    }

    private static String safeLower(String string) {
        return string == null ? "" : string.toLowerCase(Locale.ROOT);
    }

    private static String buildExtraParamString(Integer n, String string, Set<String> set, Set<String> set2) {
        String string2;
        ArrayList<String> arrayList = new ArrayList<String>();
        if (n != null) {
            arrayList.add("radius:" + n);
        }
        if (string != null) {
            arrayList.add("action:" + string);
        }
        if (set != null && !set.isEmpty()) {
            string2 = set.stream().collect(Collectors.joining(","));
            arrayList.add("include:" + string2);
        }
        if (set2 != null && !set2.isEmpty()) {
            string2 = set2.stream().collect(Collectors.joining(","));
            arrayList.add("exclude:" + string2);
        }
        return String.join(" ", arrayList);
    }

    private void offerPlayerNames(String string, List<String> list, String string2) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String string3 = player.getName();
            if (string3 == null || !string3.toLowerCase(Locale.ROOT).startsWith(string)) continue;
            list.add(string2 + string3);
        }
    }

    private void suggestActions(String string, List<String> list, String string2) {
        List<String> list2 = List.of("chat", "command", "all", "block_break", "block_place", "interact", "item_pickup", "item_drop", "container_add", "container_take");
        for (String string3 : list2) {
            if (!string3.startsWith(string.toLowerCase(Locale.ROOT))) continue;
            list.add(string2 + string3);
        }
        for (String string3 : ActionType.visibleNames()) {
            String string4 = string3.toLowerCase(Locale.ROOT);
            if (list2.contains(string4) || !string4.startsWith(string.toLowerCase(Locale.ROOT))) continue;
            list.add(string2 + string4);
        }
    }

    private UUID resolvePlayerUUID(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        try {
            return UUID.fromString(trimmed);
        }
        catch (IllegalArgumentException illegalArgumentException) {}

        Player player = Bukkit.getPlayerExact(trimmed);
        if (player != null) {
            return player.getUniqueId();
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(trimmed);
        if (offlinePlayer != null) {
            return offlinePlayer.getUniqueId();
        }

        if (trimmed.startsWith(".")) {
            OfflinePlayer alt = Bukkit.getOfflinePlayerIfCached(trimmed.substring(1));
            if (alt != null) return alt.getUniqueId();
        } else {
            OfflinePlayer alt = Bukkit.getOfflinePlayerIfCached("." + trimmed);
            if (alt != null) return alt.getUniqueId();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(trimmed);
        if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) {
            return offline.getUniqueId();
        }

        try (Connection connection = this.databaseMariadb.getDataSource().getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement("SELECT playerUUID FROM protectcore_player_actions WHERE playerName = ? OR playerName = ? OR LOWER(playerName) = LOWER(?) LIMIT 1")) {
            preparedStatement.setString(1, trimmed);
            preparedStatement.setString(2, trimmed.startsWith(".") ? trimmed.substring(1) : "." + trimmed);
            preparedStatement.setString(3, trimmed);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    String string2 = resultSet.getString(1);
                    if (string2 != null && !string2.isBlank()) {
                        return UUID.fromString(string2);
                    }
                }
            }
        }
        catch (Exception exception) {}

        return offline != null ? offline.getUniqueId() : null;
    }

    private void suggestMaterials(String string, List<String> list, String string2) {
        for (Material material : Material.values()) {
            String string3 = material.name().toLowerCase(Locale.ROOT);
            if (!string3.startsWith(string)) continue;
            list.add(string2 + string3);
        }
    }

    private void suggestMaterialsCSV(String string, List<String> list, String string2) {
        String string3 = string == null ? "" : string;
        int n = string3.lastIndexOf(44);
        String string4 = n >= 0 ? string3.substring(0, n + 1) : "";
        String string5 = n >= 0 ? string3.substring(n + 1) : string3;
        String string6 = string5.toLowerCase(Locale.ROOT).trim();
        for (Material material : Material.values()) {
            String string7 = material.name().toLowerCase(Locale.ROOT);
            if (!string7.startsWith(string6)) continue;
            String string8 = string2 + string4 + string7;
            list.add(string8);
        }
    }

    private void suggestSnippets(String string, List<String> list, String string2, List<String> list2) {
        for (String string3 : list2) {
            if (!string3.startsWith(string.toLowerCase(Locale.ROOT))) continue;
            list.add(string2 + string3);
        }
    }

    private static /* synthetic */ void lambda$execute$2(List<ActionLog> list, String string, String string2, DatabaseMariadb.WindowedResult windowedResult, Integer n, String string3, Set<String> set, Set<String> set2, CommandSender commandSender, Component component, Set<Integer> set3) {
        ArrayList<Component> arrayList = new ArrayList<Component>();
        for (ActionLog logItem : list) {
            String string4 = logItem.getPlayerName() != null ? logItem.getPlayerName() : Bukkit.getOfflinePlayer((UUID)logItem.getPlayerUUID()).getName();
            if (string4 == null) {
                string4 = "unknown";
            }
            Component component2 = PoCommand.formatLogComponent(logItem, string4);
            if (set3.contains(logItem.getId())) {
                component2 = component2.decoration(TextDecoration.STRIKETHROUGH, true);
            }
            arrayList.add(component2);
        }
        Component component3 = Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), arrayList);
        Component footer = PoCommand.formatFooterComponentWindowed(string != null ? string : "", string2, windowedResult.page, windowedResult.windowStart, windowedResult.windowEnd, windowedResult.hasPrevWindow, windowedResult.hasNextWindow, LookupSubCommand.buildExtraParamString(n, string3, set, set2));
        commandSender.sendMessage(component.append(component3).append((Component)Component.newline()).append(footer));
    }

    private static /* synthetic */ void lambda$execute$0(List<ActionLog> list, DatabaseMariadb.SearchResult searchResult, String string, String string2, int n, Integer n2, String string3, Set<String> set, Set<String> set2, CommandSender commandSender, Component component, Set<Integer> set3) {
        ArrayList<Component> arrayList = new ArrayList<Component>();
        for (ActionLog logItem : list) {
            String string4 = logItem.getPlayerName() != null ? logItem.getPlayerName() : Bukkit.getOfflinePlayer((UUID)logItem.getPlayerUUID()).getName();
            if (string4 == null) {
                string4 = "unknown";
            }
            Component component2 = PoCommand.formatLogComponent(logItem, string4);
            if (set3.contains(logItem.getId())) {
                component2 = component2.decoration(TextDecoration.STRIKETHROUGH, true);
            }
            arrayList.add(component2);
        }
        Component component3 = Component.join((JoinConfiguration)JoinConfiguration.separator((ComponentLike)Component.newline()), arrayList);
        int n3 = Math.max(1, searchResult.totalPages);
        Component footer = PoCommand.formatFooterComponent(string != null ? string : "", string2, n, n3, LookupSubCommand.buildExtraParamString(n2, string3, set, set2));
        commandSender.sendMessage(component.append(component3).append((Component)Component.newline()).append(footer));
    }
}

