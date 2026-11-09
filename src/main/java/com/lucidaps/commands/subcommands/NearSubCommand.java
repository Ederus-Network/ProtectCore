/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.event.ClickEvent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
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
import com.lucidaps.utilities.TimeUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NearSubCommand
implements SubCommand {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private final int PAGE_SIZE = 4;

    public NearSubCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
    }

    @Override
    public String getName() {
        return "near";
    }

    @Override
    public boolean execute(CommandSender commandSender, Command command, String string, String[] stringArray) {
        long l;
        String string2;
        int n;
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.onlyPlayer", Map.of()));
            return true;
        }
        Player player = (Player)commandSender;
        if (!player.hasPermission("protectcore.near")) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        String string3 = this.plugin.getConfig().getString("near.defaultPeriod", "7d");
        int n2 = 1;
        StringBuilder stringBuilder = new StringBuilder();
        block12: for (String string4 : stringArray) {
            String string5 = string4.toLowerCase(Locale.ROOT);
            n = string5.indexOf(58);
            if (n < 0) continue;
            string2 = string5.substring(0, n);
            String string6 = string4.substring(n + 1);
            switch (string2) {
                case "time": 
                case "t": {
                    if (string6.isEmpty()) continue block12;
                    string3 = string6;
                    stringBuilder.append(" time:").append(string3);
                    continue block12;
                }
                case "page": 
                case "p": {
                    try {
                        n2 = Math.max(1, Integer.parseInt(string6));
                        continue block12;
                    }
                    catch (NumberFormatException numberFormatException) {
                        n2 = 1;
                    }
                }
            }
        }
        if ("all".equalsIgnoreCase(string3)) {
            l = 0L;
        } else {
            long l2 = TimeUtil.parseTimePeriod(string3);
            if (l2 <= 0L) {
                player.sendMessage(this.plugin.getLang().get("messages.lookup.invalidTime", Map.of("period", string3)));
                return true;
            }
            l = (System.currentTimeMillis() - l2) / 1000L;
        }
        String string7 = ("/po near" + String.valueOf(stringBuilder)).trim();
        int n3 = this.plugin.getConfig().getInt("near.defaultRadius", 7);
        int n4 = this.plugin.getConfig().getInt("near.maxRadius", 10);
        n = Math.min(n3, n4);
        string2 = player.getLocation();
        boolean bl = !this.plugin.isWorldEditReportLogsEnabled();
        long l3 = Long.MAX_VALUE;
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>(List.of("CONTAINER_SNAPSHOT", "PLAYER_MESSAGE", "PLAYER_COMMAND"));
        int n5 = n2;
        String string8 = string7;
        int n6 = n;
        this.databaseMariadb.searchLogsPagedByAreaAsync(string2.getWorld().getName(), string2.getBlockX(), string2.getBlockY(), string2.getBlockZ(), n, l, Long.MAX_VALUE, null, null, null, null, null, null, null, linkedHashSet, false, bl, 4, n2, Collections.emptySet(), Collections.emptySet(), searchResult -> {
            if (searchResult.logs.isEmpty()) {
                if (n5 == 1) {
                    String string2 = this.plugin.getLang().get("messages.near.noLogs", Map.of("radius", String.valueOf(n6)));
                    player.sendMessage((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(string2));
                } else {
                    player.sendMessage((Component)LegacyComponentSerializer.legacyAmpersand().deserialize("&7No results on this page."));
                }
                return;
            }
            TextComponent textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(this.plugin.getLang().get("messages.near.header", Map.of()));
            TextComponent textComponent2 = Component.empty();
            for (ActionLog actionLog : searchResult.logs) {
                String string3;
                String string4 = string3 = actionLog.getPlayerName() != null ? actionLog.getPlayerName() : Bukkit.getOfflinePlayer((UUID)actionLog.getPlayerUUID()).getName();
                if (string3 == null) {
                    string3 = "unknown";
                }
                Component component = PoCommand.formatLogComponent(actionLog, string3);
                if (actionLog.isRolledBack()) {
                    component = component.decoration(TextDecoration.STRIKETHROUGH, true);
                }
                textComponent2 = textComponent2.append(component).append((Component)Component.newline());
            }
            Component component = NearSubCommand.pageFooter(this.plugin, string8, n5, searchResult.totalPages);
            player.sendMessage(textComponent.append((Component)textComponent2).append(component));
        });
        return true;
    }

    private static Component pageFooter(ProtectCore protectCore, String string, int n, int n2) {
        LegacyComponentSerializer legacyComponentSerializer = LegacyComponentSerializer.legacyAmpersand();
        String string2 = protectCore.getLang().get("messages.near.pageInfo", Map.of("current", String.valueOf(n), "total", String.valueOf(n2)));
        TextComponent textComponent = legacyComponentSerializer.deserialize(string2);
        String string3 = string.isBlank() ? "/po near" : string;
        TextComponent textComponent2 = Component.empty();
        if (n > 1) {
            textComponent2 = textComponent2.append(((TextComponent)Component.text((String)"\u25c0").color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)(string3 + " page:" + (n - 1))))).append((Component)Component.space());
        }
        textComponent2 = textComponent2.append((Component)textComponent).append((Component)Component.space());
        if (n < n2) {
            textComponent2 = textComponent2.append(((TextComponent)Component.text((String)"\u25b6").color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)(string3 + " page:" + (n + 1)))));
        }
        return textComponent2;
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        return new ArrayList<String>();
    }
}

