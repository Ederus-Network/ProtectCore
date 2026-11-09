package com.lucidaps.commands.subcommands;

import com.lucidaps.ProtectCore;
import com.lucidaps.commands.PoCommand;
import com.lucidaps.commands.SubCommand;
import com.lucidaps.db.ActionLog;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.utilities.TimeUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NearSubCommand implements SubCommand {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private final int PAGE_SIZE = 4;
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    public NearSubCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
    }

    @Override
    public String getName() {
        return "near";
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(this.plugin.getLang().get("messages.onlyPlayer", Map.of()));
            return true;
        }
        if (!player.hasPermission("protectcore.near")) {
            sender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }

        String timeStr = this.plugin.getConfig().getString("near.defaultPeriod", "7d");
        int page = 1;
        StringBuilder extraArgs = new StringBuilder();

        for (String arg : args) {
            String lower = arg.toLowerCase(Locale.ROOT);
            int colon = lower.indexOf(':');
            if (colon < 0) continue;
            String key = lower.substring(0, colon);
            String val = arg.substring(colon + 1);
            switch (key) {
                case "time":
                case "t":
                    if (!val.isEmpty()) {
                        timeStr = val;
                        extraArgs.append(" time:").append(timeStr);
                    }
                    break;
                case "page":
                case "p":
                    try {
                        page = Math.max(1, Integer.parseInt(val));
                    } catch (NumberFormatException ignored) {
                        page = 1;
                    }
                    break;
            }
        }

        long minTimestamp;
        if ("all".equalsIgnoreCase(timeStr)) {
            minTimestamp = 0L;
        } else {
            long periodMs = TimeUtil.parseTimePeriod(timeStr);
            if (periodMs <= 0L) {
                player.sendMessage(this.plugin.getLang().get("messages.lookup.invalidTime", Map.of("period", timeStr)));
                return true;
            }
            minTimestamp = (System.currentTimeMillis() - periodMs) / 1000L;
        }

        int defaultRadius = this.plugin.getConfig().getInt("near.defaultRadius", 7);
        int maxRadius = this.plugin.getConfig().getInt("near.maxRadius", 10);
        int radius = Math.min(defaultRadius, maxRadius);

        Location loc = player.getLocation();
        boolean hideWe = !this.plugin.isWorldEditReportLogsEnabled();
        LinkedHashSet<String> disabledActions = new LinkedHashSet<>(List.of("CONTAINER_SNAPSHOT", "PLAYER_MESSAGE", "PLAYER_COMMAND"));

        final int finalPage = page;
        final String cmdBase = ("/po near" + extraArgs).trim();
        final int finalRadius = radius;

        this.databaseMariadb.searchLogsPagedByAreaAsync(
            loc.getWorld().getName(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
            radius,
            minTimestamp,
            Long.MAX_VALUE,
            null, null, null, null, null, null, null,
            disabledActions,
            false,
            hideWe,
            this.PAGE_SIZE,
            page,
            Collections.emptySet(),
            Collections.emptySet(),
            searchResult -> {
                if (searchResult.logs.isEmpty()) {
                    if (finalPage == 1) {
                        player.sendMessage(this.plugin.getLang().get("messages.near.noLogs", Map.of("radius", String.valueOf(finalRadius))));
                    } else {
                        player.sendMessage(AMPERSAND.deserialize("&3ProtectCore &f- No results on this page."));
                    }
                    return;
                }

                TextComponent header = AMPERSAND.deserialize("&f----- &3ProtectCore &f| &3Near Results &f-----\n");
                Set<Integer> logIds = searchResult.logs.stream().map(ActionLog::getId).collect(Collectors.toCollection(LinkedHashSet::new));

                this.databaseMariadb.fetchRolledBackFlags(logIds, rolledBackIds -> {
                    List<Component> lines = new ArrayList<>();
                    for (ActionLog log : searchResult.logs) {
                        String username = log.getPlayerName() != null ? log.getPlayerName() :
                            Bukkit.getOfflinePlayer(log.getPlayerUUID()).getName();
                        if (username == null) username = "unknown";

                        Component comp = PoCommand.formatLogComponent(log, username);
                        if (rolledBackIds.contains(log.getId()) || log.isRolledBack()) {
                            comp = comp.decoration(TextDecoration.STRIKETHROUGH, true);
                        }
                        lines.add(comp);
                    }

                    Component joined = Component.join(JoinConfiguration.separator(Component.newline()), lines);
                    Component footer = pageFooter(cmdBase, finalPage, searchResult.totalPages);

                    player.sendMessage(header.append(joined).append(Component.newline()).append(footer));
                });
            }
        );
        return true;
    }

    private static Component pageFooter(String baseCmd, int page, int totalPages) {
        TextComponent.Builder builder = Component.text();
        builder.append(AMPERSAND.deserialize("&f-----\n"));

        String cmd = baseCmd.isBlank() ? "/po near" : baseCmd;

        if (page > 1) {
            Component prev = Component.text("◀", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(AMPERSAND.deserialize("&7Page " + (page - 1))))
                .clickEvent(ClickEvent.runCommand(cmd + " page:" + (page - 1)));
            builder.append(prev).append(Component.space());
        }

        builder.append(AMPERSAND.deserialize("&fPage &3" + page + "&f/&3" + totalPages));

        if (page < totalPages) {
            builder.append(Component.space());
            Component next = Component.text("▶", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(AMPERSAND.deserialize("&7Page " + (page + 1))))
                .clickEvent(ClickEvent.runCommand(cmd + " page:" + (page + 1)));
            builder.append(next);
        }

        return builder.build();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
