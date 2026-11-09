package com.lucidaps.commands.subcommands;

import com.lucidaps.ProtectCore;
import com.lucidaps.commands.PoCommand;
import com.lucidaps.commands.SubCommand;
import com.lucidaps.db.ActionLog;
import com.lucidaps.db.DatabaseMariadb;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

public class InspectSubCommand implements SubCommand {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private static final int PAGE_SIZE = 7;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private static final Set<String> EXCLUDED_ACTIONS = new LinkedHashSet<>(List.of("CONTAINER_SNAPSHOT", "PLAYER_MESSAGE", "PLAYER_COMMAND"));

    public InspectSubCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
    }

    @Override
    public String getName() {
        return "inspect";
    }

    @Override
    public boolean execute(CommandSender commandSender, Command command, String label, String[] args) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.onlyPlayer", Map.of()));
            return true;
        }
        Player player = (Player) commandSender;
        if (!player.hasPermission("protectcore.inspect")) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        if (args.length == 1) {
            boolean enabled = !this.plugin.isInspectModeEnabled(player);
            if (enabled) {
                this.plugin.enableInspectMode(player);
                player.sendMessage(this.plugin.getLang().get("messages.inspect.modeEnabled", Map.of()));
            } else {
                this.plugin.disableInspectMode(player);
                player.sendMessage(this.plugin.getLang().get("messages.inspect.modeDisabled", Map.of()));
            }
            return true;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        int x = 0;
        int y = 0;
        int z = 0;
        int page = 1;

        try {
            for (int i = 2; i < args.length; i++) {
                String arg = args[i];
                if (arg.startsWith("x:")) {
                    x = Integer.parseInt(arg.substring(2));
                } else if (arg.startsWith("y:")) {
                    y = Integer.parseInt(arg.substring(2));
                } else if (arg.startsWith("z:")) {
                    z = Integer.parseInt(arg.substring(2));
                } else if (arg.startsWith("page:")) {
                    page = Integer.parseInt(arg.substring(5));
                } else if (arg.startsWith("p:")) {
                    page = Integer.parseInt(arg.substring(2));
                }
            }
        } catch (NumberFormatException e) {
            player.sendMessage(this.plugin.getLang().get("messages.inspect.invalidNumber", Map.of()));
            return true;
        }

        final int finalX = x;
        final int finalY = y;
        final int finalZ = z;
        final int finalPage = Math.max(1, page);
        String worldName = player.getWorld().getName();
        Block block = player.getWorld().getBlockAt(x, y, z);
        boolean isContainer = block.getState() instanceof InventoryHolder;
        boolean isRightClick = "right".equalsIgnoreCase(mode);

        this.databaseMariadb.queryActions(worldName, x, y, z, 0, false, list -> {
            boolean hideWe = !this.plugin.isWorldEditReportLogsEnabled();
            List<ActionLog> filtered = list;
            if (hideWe) {
                filtered = filtered.stream().filter(l -> !isWeLog(l)).collect(Collectors.toList());
            }
            filtered = filtered.stream().filter(l -> {
                String a = l.getAction();
                return a == null || !EXCLUDED_ACTIONS.contains(a.toUpperCase(Locale.ROOT));
            }).collect(Collectors.toList());

            List<ActionLog> modeFiltered;
            if (isRightClick && isContainer) {
                modeFiltered = filtered.stream().filter(l ->
                    "CONTAINER_ADD".equalsIgnoreCase(l.getAction()) ||
                    "CONTAINER_TAKE".equalsIgnoreCase(l.getAction()) ||
                    "HOPPER_TRANSFER".equalsIgnoreCase(l.getAction()) ||
                    "INTERACT".equalsIgnoreCase(l.getAction())
                ).collect(Collectors.toList());
            } else {
                modeFiltered = filtered.stream().filter(l ->
                    !"CONTAINER_ADD".equalsIgnoreCase(l.getAction()) &&
                    !"CONTAINER_TAKE".equalsIgnoreCase(l.getAction()) &&
                    !"HOPPER_TRANSFER".equalsIgnoreCase(l.getAction())
                ).collect(Collectors.toList());
            }

            if (modeFiltered.isEmpty()) {
                if (isRightClick && isContainer) {
                    player.sendMessage(this.plugin.getLang().get("messages.inspect.noContainerLogs", Map.of("x", String.valueOf(finalX), "y", String.valueOf(finalY), "z", String.valueOf(finalZ))));
                } else {
                    player.sendMessage(this.plugin.getLang().get("messages.inspect.noLogs", Map.of("x", String.valueOf(finalX), "y", String.valueOf(finalY), "z", String.valueOf(finalZ))));
                }
                return;
            }

            int totalPages = (int) Math.ceil((double) modeFiltered.size() / PAGE_SIZE);
            if (finalPage > totalPages) {
                player.sendMessage(this.plugin.getLang().get("messages.inspect.invalidPage", Map.of("total", String.valueOf(totalPages))));
                return;
            }

            int startIdx = (finalPage - 1) * PAGE_SIZE;
            int endIdx = Math.min(startIdx + PAGE_SIZE, modeFiltered.size());
            List<ActionLog> pageLogs = modeFiltered.subList(startIdx, endIdx);

            String headerKey = (isRightClick && isContainer) ? "messages.inspect.containerHeader" : "messages.inspect.header";
            String headerStr = this.plugin.getLang().get(headerKey, Map.of(
                "x", String.valueOf(finalX),
                "y", String.valueOf(finalY),
                "z", String.valueOf(finalZ)
            ));
            Component header = this.serializer.deserialize(headerStr);

            Set<Integer> logIds = pageLogs.stream().map(ActionLog::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            this.databaseMariadb.fetchRolledBackFlags(logIds, rolledBackIds -> {
                TextComponent.Builder bodyBuilder = Component.text();
                for (ActionLog log : pageLogs) {
                    String username = log.getPlayerName() != null ? log.getPlayerName() :
                        Optional.ofNullable(Bukkit.getOfflinePlayer(log.getPlayerUUID()).getName()).orElse("unknown");
                    Component logComp = PoCommand.formatLogComponent(log, username, false);
                    if (rolledBackIds.contains(log.getId()) || log.isRolledBack()) {
                        logComp = logComp.decoration(TextDecoration.STRIKETHROUGH, true);
                    }
                    bodyBuilder.append(logComp).append(Component.newline());
                }

                Component message = header.append(bodyBuilder.build());
                if (totalPages > 1) {
                    Component footer = buildInspectorFooter(finalX, finalY, finalZ, finalPage, totalPages, mode);
                    message = message.append(this.serializer.deserialize("&f-----\n")).append(footer);
                }

                Component finalMsg = message;
                Bukkit.getScheduler().runTask(this.plugin, () -> player.sendMessage(finalMsg));
            });
        });
        return true;
    }

    private Component buildInspectorFooter(int x, int y, int z, int page, int totalPages, String mode) {
        TextComponent.Builder footer = Component.text();
        String cmdBase = "/po inspect " + mode + " x:" + x + " y:" + y + " z:" + z + " page:";

        if (page > 1) {
            Component prev = Component.text("◀", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(this.serializer.deserialize("&7Page " + (page - 1))))
                .clickEvent(ClickEvent.runCommand(cmdBase + (page - 1)));
            footer.append(prev).append(Component.space());
        }

        Component pageInfo = this.serializer.deserialize("&fPage &3" + page + "&f/&3" + totalPages);
        footer.append(pageInfo);

        if (page < totalPages) {
            footer.append(Component.space());
            Component next = Component.text("▶", NamedTextColor.DARK_AQUA)
                .hoverEvent(HoverEvent.showText(this.serializer.deserialize("&7Page " + (page + 1))))
                .clickEvent(ClickEvent.runCommand(cmdBase + (page + 1)));
            footer.append(next);
        }
        return footer.build();
    }

    private static boolean isWeLog(ActionLog actionLog) {
        try {
            String metadata = actionLog.getMetadata();
            if (metadata != null) {
                String metaLower = metadata.toLowerCase(Locale.ROOT);
                if (metaLower.contains("\"we\":true") || metaLower.contains("\"worldedit\"") ||
                    metaLower.contains("\"fawe\"") || metaLower.contains("\"source\":\"we\"") ||
                    metaLower.contains("\"source\":\"worldedit\"")) {
                    return true;
                }
            }
            String details = actionLog.getDetails();
            if (details != null) {
                String detLower = details.toLowerCase(Locale.ROOT);
                if (detLower.contains("worldedit") || detLower.contains("fawe")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }
}
