package com.lucidaps.commands.subcommands;

import com.lucidaps.ProtectCore;
import com.lucidaps.commands.PoCommand;
import com.lucidaps.commands.SubCommand;
import com.lucidaps.db.ActionLog;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.logging.ActionType;
import com.lucidaps.utilities.TimeUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
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

public class LookupSubCommand implements SubCommand {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private final int PAGE_SIZE;
    private static final List<String> TIME_SNIPPETS = List.of("5m", "10m", "30m", "1h", "6h", "1d");
    private static final List<String> RADIUS_SNIPPETS = List.of("5", "10", "20", "50");
    private final int defaultRadius;

    public record LastLookupQuery(
        String user,
        Integer radius,
        String time,
        String action,
        LinkedHashSet<String> include,
        LinkedHashSet<String> exclude
    ) {}

    private static final Map<UUID, LastLookupQuery> lastQueries = new ConcurrentHashMap<>();

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

    private static String canonicalKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return switch (k) {
            case "u" -> "user";
            case "r" -> "radius";
            case "t" -> "time";
            case "p" -> "page";
            case "a" -> "action";
            case "i" -> "include";
            case "e" -> "exclude";
            default -> k;
        };
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("protectcore.lookup")) {
            sender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }

        if (args.length == 2 && sender instanceof Player player) {
            String singleArg = args[1].trim();
            int requestedPage = -1;
            if (singleArg.matches("\\d+")) {
                requestedPage = Integer.parseInt(singleArg);
            } else if (singleArg.toLowerCase(Locale.ROOT).startsWith("page:") && singleArg.substring(5).matches("\\d+")) {
                requestedPage = Integer.parseInt(singleArg.substring(5));
            } else if (singleArg.toLowerCase(Locale.ROOT).startsWith("p:") && singleArg.substring(2).matches("\\d+")) {
                requestedPage = Integer.parseInt(singleArg.substring(2));
            }

            if (requestedPage > 0) {
                LastLookupQuery last = lastQueries.get(player.getUniqueId());
                if (last != null) {
                    return executeQuery(player, last.user, last.radius, last.time, requestedPage, last.action, last.include, last.exclude);
                } else {
                    return executeQuery(player, null, this.defaultRadius, "1d", requestedPage, null, new LinkedHashSet<>(), new LinkedHashSet<>());
                }
            }
        }

        String targetUser = null;
        Integer radius = null;
        String timeStr = "1d";
        int page = 1;
        String actionType = null;
        LinkedHashSet<String> includeMaterials = new LinkedHashSet<>();
        LinkedHashSet<String> excludeMaterials = new LinkedHashSet<>();
        List<String> unknownInclude = new ArrayList<>();
        List<String> unknownExclude = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            String argLower = arg.toLowerCase(Locale.ROOT);
            int colonIdx = argLower.indexOf(':');

            if (colonIdx >= 0) {
                String rawKey = argLower.substring(0, colonIdx);
                String canonKey = canonicalKey(rawKey);
                String val = arg.substring(colonIdx + 1);
                if (val.isEmpty()) {
                    if (i + 1 >= args.length) {
                        sender.sendMessage(ChatColor.RED + "Missing value after " + canonKey + ":");
                        return true;
                    }
                    val = args[++i];
                }

                try {
                    switch (canonKey) {
                        case "user":
                            targetUser = val;
                            break;
                        case "radius":
                            radius = Integer.parseInt(val);
                            break;
                        case "time":
                            timeStr = val;
                            break;
                        case "page":
                            page = Integer.parseInt(val);
                            break;
                        case "action":
                            String rawAction = val.toUpperCase(Locale.ROOT);
                            if (rawAction.equals("CHAT") || rawAction.equals("MSG") || rawAction.equals("MESSAGE") || rawAction.equals("PLAYER_MESSAGE")) {
                                actionType = "PLAYER_MESSAGE";
                            } else if (rawAction.equals("CMD") || rawAction.equals("COMMAND") || rawAction.equals("PLAYER_COMMAND")) {
                                actionType = "PLAYER_COMMAND";
                            } else if (rawAction.equals("ALL")) {
                                actionType = "ALL";
                            } else {
                                actionType = rawAction;
                            }
                            break;
                        case "include":
                            parseMaterialCSV(val, includeMaterials, unknownInclude);
                            break;
                        case "exclude":
                            parseMaterialCSV(val, excludeMaterials, unknownExclude);
                            break;
                        default:
                            if (targetUser == null) {
                                targetUser = arg;
                            }
                            break;
                    }
                } catch (NumberFormatException e) {
                    if ("radius".equals(canonKey)) {
                        sender.sendMessage(ChatColor.RED + "Invalid radius: " + val);
                    } else if ("page".equals(canonKey)) {
                        sender.sendMessage(ChatColor.RED + "Invalid page: " + val);
                    }
                    return true;
                }
                continue;
            }

            if (targetUser == null) {
                targetUser = arg;
            }
        }

        if (!unknownInclude.isEmpty() || !unknownExclude.isEmpty()) {
            List<String> ignored = new ArrayList<>();
            if (!unknownInclude.isEmpty()) {
                ignored.add("include: " + String.join(", ", unknownInclude));
            }
            if (!unknownExclude.isEmpty()) {
                ignored.add("exclude: " + String.join(", ", unknownExclude));
            }
            sender.sendMessage(ChatColor.YELLOW + "Unknown material(s) ignored: " + String.join(" | ", ignored));
        }

        if (targetUser == null && radius == null) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "From console, specify either user:<name> or radius:<blocks> for area lookup.");
                return true;
            }
            radius = this.defaultRadius;
        }

        return executeQuery(sender, targetUser, radius, timeStr, page, actionType, includeMaterials, excludeMaterials);
    }

    private boolean executeQuery(
        CommandSender sender,
        String targetUser,
        Integer radius,
        String timeStr,
        int page,
        String actionType,
        LinkedHashSet<String> includeMaterials,
        LinkedHashSet<String> excludeMaterials
    ) {
        long periodMs = TimeUtil.parseTimePeriod(timeStr);
        if (periodMs <= 0L) {
            sender.sendMessage(this.plugin.getLang().get("messages.lookup.invalidTime", Map.of("period", timeStr)));
            return true;
        }

        long minTimestamp = System.currentTimeMillis() - periodMs;
        long maxTimestamp = Long.MAX_VALUE;

        LinkedHashSet<String> disabledActions = new LinkedHashSet<>();
        disabledActions.add("CONTAINER_SNAPSHOT");
        if (actionType == null || (!actionType.equals("PLAYER_MESSAGE") && !actionType.equals("ALL"))) {
            disabledActions.add("PLAYER_MESSAGE");
        }
        if (actionType == null || (!actionType.equals("PLAYER_COMMAND") && !actionType.equals("ALL"))) {
            disabledActions.add("PLAYER_COMMAND");
        }

        String filterAction = "ALL".equals(actionType) ? null : actionType;
        boolean hideWe = !this.plugin.isWorldEditReportLogsEnabled();
        int windowPages = Math.max(1, this.plugin.getConfig().getInt("lookup.windowPages", 10));

        if (sender instanceof Player player) {
            lastQueries.put(player.getUniqueId(), new LastLookupQuery(targetUser, radius, timeStr, filterAction, includeMaterials, excludeMaterials));
        }

        final String finalUser = targetUser;
        final Integer finalRadius = radius;
        final String finalTimeStr = timeStr;
        final int finalPage = Math.max(1, page);
        final String finalAction = filterAction;

        Consumer<DatabaseMariadb.SearchResult> areaResultConsumer = searchResult -> {
            List<ActionLog> logs = searchResult.logs;
            if (logs.isEmpty()) {
                sender.sendMessage(this.plugin.getLang().get("messages.lookup.noLogs", Map.of(
                    "user", finalUser != null ? finalUser : "anyone",
                    "period", finalTimeStr
                )));
                return;
            }

            TextComponent header = LegacyComponentSerializer.legacyAmpersand().deserialize(
                this.plugin.getLang().get("messages.lookup.header", Map.of())
            );

            Set<Integer> logIds = logs.stream().map(ActionLog::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            this.databaseMariadb.fetchRolledBackFlags(logIds, rolledBackIds -> {
                List<Component> logLines = new ArrayList<>();
                for (ActionLog log : logs) {
                    String username = log.getPlayerName() != null ? log.getPlayerName() :
                        Bukkit.getOfflinePlayer(log.getPlayerUUID()).getName();
                    if (username == null) username = "unknown";

                    Component comp = PoCommand.formatLogComponent(log, username);
                    if (rolledBackIds.contains(log.getId()) || log.isRolledBack()) {
                        comp = comp.decoration(TextDecoration.STRIKETHROUGH, true);
                    }
                    logLines.add(comp);
                }

                Component joinedLogs = Component.join(JoinConfiguration.separator(Component.newline()), logLines);
                int totalPages = Math.max(1, searchResult.totalPages);
                Component footer = PoCommand.formatFooterComponent(
                    finalUser != null ? finalUser : "",
                    finalTimeStr,
                    finalPage,
                    totalPages,
                    buildExtraParamString(finalRadius, finalAction, includeMaterials, excludeMaterials)
                );

                sender.sendMessage(header.append(joinedLogs).append(Component.newline()).append(footer));
            });
        };

        Consumer<DatabaseMariadb.WindowedResult> windowedResultConsumer = windowedResult -> {
            List<ActionLog> logs = windowedResult.logs;
            if (logs.isEmpty()) {
                sender.sendMessage(this.plugin.getLang().get("messages.lookup.noLogs", Map.of(
                    "user", finalUser != null ? finalUser : "anyone",
                    "period", finalTimeStr
                )));
                return;
            }

            TextComponent header = LegacyComponentSerializer.legacyAmpersand().deserialize(
                this.plugin.getLang().get("messages.lookup.header", Map.of())
            );

            Set<Integer> logIds = logs.stream().map(ActionLog::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            this.databaseMariadb.fetchRolledBackFlags(logIds, rolledBackIds -> {
                List<Component> logLines = new ArrayList<>();
                for (ActionLog log : logs) {
                    String username = log.getPlayerName() != null ? log.getPlayerName() :
                        Bukkit.getOfflinePlayer(log.getPlayerUUID()).getName();
                    if (username == null) username = "unknown";

                    Component comp = PoCommand.formatLogComponent(log, username);
                    if (rolledBackIds.contains(log.getId()) || log.isRolledBack()) {
                        comp = comp.decoration(TextDecoration.STRIKETHROUGH, true);
                    }
                    logLines.add(comp);
                }

                Component joinedLogs = Component.join(JoinConfiguration.separator(Component.newline()), logLines);
                Component footer = PoCommand.formatFooterComponentWindowed(
                    finalUser != null ? finalUser : "",
                    finalTimeStr,
                    windowedResult.page,
                    windowedResult.windowStart,
                    windowedResult.windowEnd,
                    windowedResult.hasPrevWindow,
                    windowedResult.hasNextWindow,
                    buildExtraParamString(finalRadius, finalAction, includeMaterials, excludeMaterials)
                );

                sender.sendMessage(header.append(joinedLogs).append(Component.newline()).append(footer));
            });
        };

        if (finalRadius != null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Radius lookup must be run by an in-game player.");
                return true;
            }

            String userUUID = null;
            if (finalUser != null && !finalUser.isBlank()) {
                UUID uuid = resolvePlayerUUID(finalUser);
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown player: " + finalUser);
                    return true;
                }
                userUUID = uuid.toString();
            }

            this.databaseMariadb.searchLogsPagedByAreaAsync(
                player.getWorld().getName(),
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ(),
                finalRadius,
                minTimestamp / 1000L,
                maxTimestamp,
                userUUID,
                null, null, null, null, null,
                finalAction,
                disabledActions,
                false,
                hideWe,
                this.PAGE_SIZE,
                finalPage,
                includeMaterials,
                excludeMaterials,
                areaResultConsumer
            );
        } else {
            if (finalUser == null || finalUser.isBlank()) {
                sender.sendMessage(ChatColor.RED + "From console, specify either user:<name> or radius:<blocks> for area lookup.");
                return true;
            }

            UUID uuid = resolvePlayerUUID(finalUser);
            if (uuid == null) {
                sender.sendMessage(ChatColor.RED + "Unknown player: " + finalUser);
                return true;
            }

            this.databaseMariadb.searchLogsWindowedByUserAsync(
                uuid.toString(),
                minTimestamp / 1000L,
                maxTimestamp,
                null, null, null, null, null,
                finalAction,
                disabledActions,
                false,
                hideWe,
                this.PAGE_SIZE,
                finalPage,
                windowPages,
                includeMaterials,
                excludeMaterials,
                windowedResultConsumer
            );
        }

        return true;
    }

    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (!sender.hasPermission("protectcore.lookup")) {
            return suggestions;
        }

        if (args.length == 0 || (args.length == 1 && args[0].isBlank())) {
            offerPlayerNames("", suggestions, "");
            return suggestions;
        }

        if (args.length == 1) {
            String lower = args[0].toLowerCase(Locale.ROOT);
            offerPlayerNames(lower, suggestions, "");
            return suggestions;
        }

        Set<String> usedKeys = new HashSet<>();
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            int colon = arg.indexOf(':');
            if (colon >= 0) {
                String rawKey = arg.substring(0, colon);
                usedKeys.add(canonicalKey(rawKey));
            }
        }

        Consumer<String> addIfUnused = s -> {
            String sub = s.substring(0, s.length() - 1);
            String canon = canonicalKey(sub);
            if (!usedKeys.contains(canon)) {
                suggestions.add(s);
            }
        };

        String lastArg = args[args.length - 1];
        String lastLower = lastArg.toLowerCase(Locale.ROOT);

        if (lastLower.contains(":")) {
            String[] parts = lastLower.split(":", 2);
            String canonPrefix = canonicalKey(parts[0]);
            String val = parts.length == 2 ? parts[1] : "";

            switch (canonPrefix) {
                case "user":
                    if (!usedKeys.contains("user")) {
                        offerPlayerNames(val, suggestions, "user:");
                    }
                    break;
                case "action":
                    if (!usedKeys.contains("action")) {
                        suggestActions(val, suggestions, "action:");
                    }
                    break;
                case "time":
                    suggestSnippets(val, suggestions, "time:", TIME_SNIPPETS);
                    break;
                case "radius":
                    suggestSnippets(val, suggestions, "radius:", RADIUS_SNIPPETS);
                    break;
                case "include":
                    suggestMaterialsCSV(val, suggestions, "include:");
                    break;
                case "exclude":
                    suggestMaterialsCSV(val, suggestions, "exclude:");
                    break;
            }
            return suggestions;
        }

        String prevArg = args[args.length - 2].toLowerCase(Locale.ROOT);
        if (prevArg.endsWith(":")) {
            prevArg = prevArg.substring(0, prevArg.length() - 1);
        }
        String canonicalPrev = canonicalKey(prevArg);

        switch (canonicalPrev) {
            case "user":
                offerPlayerNames(lastLower, suggestions, "");
                break;
            case "action":
                suggestActions(lastLower, suggestions, "");
                break;
            case "time":
                suggestSnippets(lastLower, suggestions, "", TIME_SNIPPETS);
                break;
            case "radius":
                suggestSnippets(lastLower, suggestions, "", RADIUS_SNIPPETS);
                break;
            case "include":
            case "exclude":
                suggestMaterialsCSV(lastLower, suggestions, "");
                break;
            default:
                for (String tag : List.of("user:", "radius:", "time:", "page:", "action:", "include:", "exclude:")) {
                    if (tag.startsWith(lastLower)) {
                        addIfUnused.accept(tag);
                    }
                }
                break;
        }
        return suggestions;
    }

    private static void parseMaterialCSV(String csv, Set<String> validSet, List<String> unknownList) {
        if (csv == null) return;
        for (String item : csv.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;
            String matName = trimmed;
            int colon = matName.indexOf(':');
            if (colon >= 0) {
                matName = matName.substring(colon + 1);
            }
            Material m = Material.matchMaterial(matName, false);
            if (m != null) {
                validSet.add(m.name().toLowerCase(Locale.ROOT));
            } else {
                unknownList.add(trimmed);
            }
        }
    }

    private static String buildExtraParamString(Integer radius, String action, Set<String> include, Set<String> exclude) {
        List<String> params = new ArrayList<>();
        if (radius != null) {
            params.add("radius:" + radius);
        }
        if (action != null) {
            params.add("action:" + action);
        }
        if (include != null && !include.isEmpty()) {
            params.add("include:" + String.join(",", include));
        }
        if (exclude != null && !exclude.isEmpty()) {
            params.add("exclude:" + String.join(",", exclude));
        }
        return String.join(" ", params);
    }

    private void offerPlayerNames(String prefix, List<String> dest, String tagPrefix) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                dest.add(tagPrefix + name);
            }
        }
    }

    private void suggestActions(String prefix, List<String> dest, String tagPrefix) {
        List<String> common = List.of("chat", "command", "all", "block_break", "block_place", "interact", "item_pickup", "item_drop", "container_add", "container_take");
        for (String act : common) {
            if (act.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                dest.add(tagPrefix + act);
            }
        }
        for (String act : ActionType.visibleNames()) {
            String lower = act.toLowerCase(Locale.ROOT);
            if (!common.contains(lower) && lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                dest.add(tagPrefix + lower);
            }
        }
    }

    private UUID resolvePlayerUUID(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {}

        Player player = Bukkit.getPlayerExact(trimmed);
        if (player != null) {
            return player.getUniqueId();
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(trimmed);
        if (cached != null) {
            return cached.getUniqueId();
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
                    String str = resultSet.getString(1);
                    if (str != null && !str.isBlank()) {
                        return UUID.fromString(str);
                    }
                }
            }
        } catch (Exception ignored) {}

        return offline != null ? offline.getUniqueId() : null;
    }

    private void suggestMaterialsCSV(String input, List<String> dest, String tagPrefix) {
        String s = input == null ? "" : input;
        int lastComma = s.lastIndexOf(',');
        String base = lastComma >= 0 ? s.substring(0, lastComma + 1) : "";
        String current = (lastComma >= 0 ? s.substring(lastComma + 1) : s).toLowerCase(Locale.ROOT).trim();

        for (Material material : Material.values()) {
            String name = material.name().toLowerCase(Locale.ROOT);
            if (name.startsWith(current)) {
                dest.add(tagPrefix + base + name);
            }
        }
    }

    private void suggestSnippets(String input, List<String> dest, String tagPrefix, List<String> snippets) {
        for (String snippet : snippets) {
            if (snippet.startsWith(input.toLowerCase(Locale.ROOT))) {
                dest.add(tagPrefix + snippet);
            }
        }
    }
}
