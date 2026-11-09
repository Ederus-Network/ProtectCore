package com.lucidaps.commands;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lucidaps.ProtectCore;
import com.lucidaps.commands.subcommands.EditorSubCommand;
import com.lucidaps.commands.subcommands.InspectSubCommand;
import com.lucidaps.commands.subcommands.LookupSubCommand;
import com.lucidaps.commands.subcommands.NearSubCommand;
import com.lucidaps.commands.subcommands.PurgeSubCommand;
import com.lucidaps.commands.subcommands.ReloadSubCommand;
import com.lucidaps.commands.subcommands.RollbackSubCommand;
import com.lucidaps.commands.subcommands.StatusSubCommand;
import com.lucidaps.db.ActionLog;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.db.DbPurger;
import com.lucidaps.utilities.Lang;
import com.lucidaps.utilities.LogFormatting;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class PoCommand implements CommandExecutor, TabCompleter {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();
    private static String teleportHoverMessage = "Click to teleport.";
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    public PoCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb, DbPurger dbPurger) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;

        LookupSubCommand lookupSubCommand = new LookupSubCommand(protectCore, databaseMariadb);
        this.registerSubCommand(lookupSubCommand);
        this.subCommands.put("l", lookupSubCommand);

        InspectSubCommand inspectSubCommand = new InspectSubCommand(protectCore, databaseMariadb);
        this.registerSubCommand(inspectSubCommand);
        this.subCommands.put("i", inspectSubCommand);

        this.registerSubCommand(new NearSubCommand(protectCore, databaseMariadb));
        this.registerSubCommand(new EditorSubCommand(protectCore, databaseMariadb));
        this.registerSubCommand(new RollbackSubCommand(protectCore, databaseMariadb));
        this.registerSubCommand(new ReloadSubCommand(protectCore));
        this.registerSubCommand(new PurgeSubCommand(protectCore, dbPurger));
        this.registerSubCommand(new StatusSubCommand(protectCore, databaseMariadb, dbPurger));

        teleportHoverMessage = protectCore.getLang().get("messages.teleportHover", Map.of());
    }

    private void registerSubCommand(SubCommand subCommand) {
        this.subCommands.put(subCommand.getName().toLowerCase(Locale.ROOT), subCommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("protectcore.commands")) {
            sender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        if (args.length < 1) {
            String available = String.join("|", this.subCommands.keySet());
            sender.sendMessage(this.plugin.getLang().get("messages.usageGeneric", Map.of("subcommands", available)));
            return true;
        }
        SubCommand subCommand = this.subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand == null) {
            String available = String.join(", ", this.subCommands.keySet());
            sender.sendMessage(this.plugin.getLang().get("messages.unknownSubcommand", Map.of("subcommands", available)));
            return true;
        }
        return subCommand.execute(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return this.subCommands.keySet().stream()
                .filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }
        SubCommand subCommand = this.subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subCommand != null) {
            return subCommand.tabComplete(sender, command, alias, args);
        }
        return Collections.emptyList();
    }

    public static Component applyMetadataHover(Component component, ActionLog actionLog) {
        if (actionLog.getMetadata() != null && !actionLog.getMetadata().isEmpty()) {
            String metaStr = formatMetadata(actionLog.getMetadata());
            if (metaStr != null && !metaStr.isBlank()) {
                TextComponent hoverText = AMPERSAND.deserialize(metaStr);
                return component.hoverEvent(HoverEvent.showText(hoverText));
            }
        }
        return component;
    }

    public static Component formatLogComponent(ActionLog actionLog, String username, boolean includeLocation) {
        String timeAgo = formatTimeAgo(actionLog.getTimestamp());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String exactTime = sdf.format(actionLog.getTimestamp());

        Component timeComp = Component.text(timeAgo, NamedTextColor.GRAY)
            .hoverEvent(HoverEvent.showText(Component.text(exactTime, NamedTextColor.GRAY)));
        Component sepComp = Component.text(" - ", NamedTextColor.WHITE);

        String details = actionLog.getDetails();
        String detailsLower = details == null ? "" : details.toLowerCase(Locale.ROOT);
        String action = actionLog.getAction() != null ? actionLog.getAction().toUpperCase(Locale.ROOT) : "DEFAULT";

        String effectiveUser = username != null && !username.isBlank() ? username : "unknown";

        Component line1;

        if ("PLAYER_MESSAGE".equals(action)) {
            String msg = details != null ? details : "";
            Component playerComp = Component.text(effectiveUser + ": ", NamedTextColor.DARK_AQUA);
            Component msgComp = Component.text(msg, NamedTextColor.WHITE);
            line1 = timeComp.append(sepComp).append(playerComp).append(msgComp);
        } else if ("PLAYER_COMMAND".equals(action)) {
            String cmd = details != null ? details : "";
            Component playerComp = Component.text(effectiveUser + ": ", NamedTextColor.DARK_AQUA);
            Component cmdComp = Component.text(cmd, NamedTextColor.YELLOW);
            line1 = timeComp.append(sepComp).append(playerComp).append(cmdComp);
        } else {
            LogFormatting.LogFormatter formatter = LogFormatting.LogFormatter.getFormatter(action);
            LogFormatting.FormattedLogParts parts = formatter.format(actionLog, detailsLower, timeAgo, includeLocation);

            if (parts.overrideComponent() != null) {
                line1 = timeComp.append(sepComp).append(parts.overrideComponent());
            } else {
                String symbol = parts.symbol();
                String verb = parts.actionVerb();
                String detail = parts.detailSuffix();
                String user = parts.usernameOverride() != null ? parts.usernameOverride() : effectiveUser;

                String rowText;
                if (parts.detailContainsVerb()) {
                    rowText = symbol + " &3" + user + " " + detail + "&f.";
                } else {
                    rowText = symbol + " &3" + user + " &f" + verb + " " + detail + "&f.";
                }
                line1 = timeComp.append(sepComp).append(AMPERSAND.deserialize(rowText));
            }
        }

        line1 = applyMetadataHover(line1, actionLog);

        if (actionLog.isRolledBack()) {
            line1 = line1.decoration(TextDecoration.STRIKETHROUGH, true);
        }

        if (includeLocation) {
            return line1.append(Component.newline()).append(buildLocationLine(actionLog));
        }
        return line1;
    }

    public static Component formatLogComponent(ActionLog actionLog, String username) {
        return formatLogComponent(actionLog, username, true);
    }

    public static void updateTeleportHover(Lang lang) {
        teleportHoverMessage = lang.get("messages.teleportHover", Map.of());
    }

    public static String getActionTag(String action) {
        if (action == null) return "block";
        return switch (action.toUpperCase(Locale.ROOT)) {
            case "BLOCK_PLACE", "BLOCK_BREAK", "BLOCK_BURN", "BLOCK_EXPLODE", "BLOCK_FORM", "LEAVES_DECAY", "LIQUID_FLOW", "STRUCTURE_GROW" -> "block";
            case "CONTAINER_ADD", "CONTAINER_TAKE", "CONTAINER_SNAPSHOT", "HOPPER_TRANSFER" -> "container";
            case "INTERACT" -> "click";
            case "ITEM_PICKUP", "ITEM_DROP" -> "item";
            case "ENTITY_DEATH" -> "kill";
            case "ENTITY_PLACE", "ENTITY_EXPLODE" -> "entity";
            case "PLAYER_MESSAGE" -> "chat";
            case "PLAYER_COMMAND" -> "command";
            default -> action.toLowerCase(Locale.ROOT).replace('_', '-');
        };
    }

    private static Component buildLocationLine(ActionLog actionLog) {
        String coords = "(x" + actionLog.getX() + "/y" + actionLog.getY() + "/z" + actionLog.getZ() + "/" + actionLog.getWorld() + ")";
        World world = Bukkit.getWorld(actionLog.getWorld());
        if (world == null) {
            Optional<World> match = Bukkit.getWorlds().stream()
                .filter(w -> w.getName().equalsIgnoreCase(actionLog.getWorld()) || w.getKey().toString().equalsIgnoreCase(actionLog.getWorld()))
                .findFirst();
            if (match.isPresent()) {
                world = match.get();
            }
        }
        String worldKey = world != null ? world.getKey().toString() : actionLog.getWorld();
        String token = UUID.randomUUID().toString();
        PcTpCommand.registerToken(token);
        String tpCmd = "/pc_tp " + token + " " + worldKey + " " + actionLog.getX() + " " + actionLog.getY() + " " + actionLog.getZ();

        String hoverMsg = (teleportHoverMessage != null && !teleportHoverMessage.isBlank()) ? teleportHoverMessage : "Click to teleport";

        Component coordComp = Component.text(coords, NamedTextColor.GRAY)
            .hoverEvent(HoverEvent.showText(AMPERSAND.deserialize(hoverMsg)))
            .clickEvent(ClickEvent.runCommand(tpCmd));

        String actionTag = getActionTag(actionLog.getAction());
        Component actionComp = Component.text(" (a:" + actionTag + ")", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, true);

        return Component.text("         ^ ", NamedTextColor.GRAY)
            .append(coordComp)
            .append(actionComp);
    }

    public static String formatMetadata(String string) {
        try {
            JsonObject jsonObject = new Gson().fromJson(string, JsonObject.class);
            if (jsonObject.has("item") && jsonObject.get("item").isJsonObject()) {
                jsonObject = jsonObject.getAsJsonObject("item");
            }
            StringBuilder sb = new StringBuilder();
            if (jsonObject.has("displayName")) {
                sb.append("§dName: §f").append(jsonObject.get("displayName").getAsString()).append('\n');
            }
            if (jsonObject.has("enchantments")) {
                sb.append("§dEnchantments:\n");
                jsonObject.getAsJsonObject("enchantments").entrySet().forEach(entry ->
                    sb.append("  §7- ").append(entry.getKey()).append(": §f").append(entry.getValue().getAsInt()).append('\n')
                );
            }
            if (jsonObject.has("lore")) {
                sb.append("§dLore:\n");
                jsonObject.getAsJsonArray("lore").forEach(element ->
                    sb.append("  §7- ").append(element.getAsString()).append('\n')
                );
            }
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
                sb.setLength(sb.length() - 1);
            }
            return sb.toString();
        } catch (Exception e) {
            return string;
        }
    }

    public static String formatTimeAgo(Timestamp timestamp) {
        long diff = System.currentTimeMillis() - timestamp.getTime();
        long seconds = Math.max(0, diff / 1000L);
        if (seconds >= 86400L) {
            return String.format(Locale.ROOT, "%.2f/d ago", (double) seconds / 86400.0);
        }
        if (seconds >= 3600L) {
            return String.format(Locale.ROOT, "%.2f/h ago", (double) seconds / 3600.0);
        }
        if (seconds >= 60L) {
            return String.format(Locale.ROOT, "%.2f/m ago", (double) seconds / 60.0);
        }
        return String.format(Locale.ROOT, "%.2f/s ago", (double) seconds);
    }

    public static Component formatFooterComponent(String user, String time, int page, int totalPages) {
        return formatFooterComponent(user, time, page, totalPages, "");
    }

    public static Component formatFooterComponent(String user, String time, int page, int totalPages, String extraParams) {
        TextComponent.Builder builder = Component.text();
        builder.append(AMPERSAND.deserialize("&f-----\n"));

        String extra = (extraParams != null && !extraParams.isBlank()) ? " " + extraParams.trim() : "";
        String cmdBase = "/po lookup" + (user != null && !user.isBlank() ? " user:" + user : "") + " time:" + time + extra + " page:";

        if (page > 1) {
            Component prev = Component.text("◀", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(AMPERSAND.deserialize("&7Page " + (page - 1))))
                .clickEvent(ClickEvent.runCommand(cmdBase + (page - 1)));
            builder.append(prev).append(Component.space());
        }

        builder.append(AMPERSAND.deserialize("&fPage &3" + page + "&f/&3" + totalPages));

        if (page < totalPages) {
            builder.append(Component.space());
            Component next = Component.text("▶", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(AMPERSAND.deserialize("&7Page " + (page + 1))))
                .clickEvent(ClickEvent.runCommand(cmdBase + (page + 1)));
            builder.append(next);
        }
        return builder.build();
    }

    public static Component formatFooterComponentWindowed(String user, String time, int page, int windowStart, int windowEnd, boolean hasPrevWindow, boolean hasNextWindow, String extraParams) {
        TextComponent.Builder builder = Component.text();
        builder.append(AMPERSAND.deserialize("&f-----\n"));

        String extra = (extraParams != null && !extraParams.isBlank()) ? " " + extraParams.trim() : "";
        String cmdBase = "/po lookup" + (user != null && !user.isBlank() ? " user:" + user : "") + " time:" + time + extra + " page:";

        if (hasPrevWindow) {
            int jumpTarget = Math.max(1, windowStart - 1);
            Component jumpPrev = Component.text("⏮", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(AMPERSAND.deserialize("&7Jump to pages " + Math.max(1, windowStart - 10) + "–" + (windowStart - 1))))
                .clickEvent(ClickEvent.runCommand(cmdBase + jumpTarget));
            builder.append(jumpPrev).append(Component.space());
        }

        if (page > windowStart) {
            Component prev = Component.text("◀", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(AMPERSAND.deserialize("&7Page " + (page - 1))))
                .clickEvent(ClickEvent.runCommand(cmdBase + (page - 1)));
            builder.append(prev).append(Component.space());
        }

        builder.append(AMPERSAND.deserialize("&fPage &3" + page + " &7(" + windowStart + "–" + windowEnd + ")"));

        if (page < windowEnd) {
            builder.append(Component.space());
            Component next = Component.text("▶", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(AMPERSAND.deserialize("&7Page " + (page + 1))))
                .clickEvent(ClickEvent.runCommand(cmdBase + (page + 1)));
            builder.append(next);
        }

        if (hasNextWindow) {
            builder.append(Component.space());
            int jumpTarget = windowEnd + 1;
            Component jumpNext = Component.text("⏭", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(AMPERSAND.deserialize("&7Jump to pages " + (windowEnd + 1) + "–" + (windowEnd + 10))))
                .clickEvent(ClickEvent.runCommand(cmdBase + jumpTarget));
            builder.append(jumpNext);
        }

        return builder.build();
    }
}
