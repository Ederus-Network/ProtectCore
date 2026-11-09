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
 *  org.bukkit.block.Block
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.plugin.Plugin
 */
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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public class InspectSubCommand
implements SubCommand,
TabCompleter {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private static final int PAGE_SIZE = 7;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private static final Set<String> EXCLUDED_ACTIONS = new LinkedHashSet<String>(List.of("CONTAINER_SNAPSHOT", "PLAYER_MESSAGE", "PLAYER_COMMAND"));

    public InspectSubCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
    }

    @Override
    public String getName() {
        return "inspect";
    }

    @Override
    public boolean execute(CommandSender commandSender, Command command, String string, String[] stringArray) {
        int n;
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.onlyPlayer", Map.of()));
            return true;
        }
        Player player = (Player)commandSender;
        if (!player.hasPermission("protectcore.inspect")) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        if (stringArray.length == 1) {
            String string2;
            String string3 = string2 = this.plugin.isInspectModeEnabled(player) ? "messages.inspect.modeDisabled" : "messages.inspect.modeEnabled";
            if (this.plugin.isInspectModeEnabled(player)) {
                this.plugin.disableInspectMode(player);
            } else {
                this.plugin.enableInspectMode(player);
            }
            player.sendMessage(this.plugin.getLang().get(string2, Map.of()));
            return true;
        }
        String string4 = stringArray[1].toLowerCase();
        int n2 = 0;
        int n3 = 0;
        int n4 = 0;
        int n5 = 1;
        try {
            for (n = 2; n < stringArray.length; ++n) {
                if (stringArray[n].startsWith("x:")) {
                    n2 = Integer.parseInt(stringArray[n].substring(2));
                    continue;
                }
                if (stringArray[n].startsWith("y:")) {
                    n3 = Integer.parseInt(stringArray[n].substring(2));
                    continue;
                }
                if (stringArray[n].startsWith("z:")) {
                    n4 = Integer.parseInt(stringArray[n].substring(2));
                    continue;
                }
                if (!stringArray[n].startsWith("page:")) continue;
                n5 = Integer.parseInt(stringArray[n].substring(5));
            }
        }
        catch (NumberFormatException numberFormatException) {
            player.sendMessage(this.plugin.getLang().get("messages.inspect.invalidNumber", Map.of()));
            return true;
        }
        n = n2;
        int n6 = n3;
        int n7 = n4;
        int n8 = n5;
        String string5 = player.getWorld().getName();
        Block block = player.getWorld().getBlockAt(n, n6, n7);
        boolean bl = block.getState() instanceof InventoryHolder;
        this.databaseMariadb.queryActions(string5, n, n6, n7, 0, false, list -> {
            boolean bl2 = !this.plugin.isWorldEditReportLogsEnabled();
            List list2 = list;
            if (bl2) {
                list2 = list.stream().filter(actionLog -> !InspectSubCommand.isWeLog(actionLog)).collect(Collectors.toList());
            }
            if ((list2 = list2.stream().filter(actionLog -> {
                String string = actionLog.getAction();
                return string == null || !EXCLUDED_ACTIONS.contains(string.toUpperCase(Locale.ROOT));
            }).collect(Collectors.toList())).isEmpty()) {
                player.sendMessage(this.plugin.getLang().get("messages.inspect.noLogs", Map.of("x", String.valueOf(n), "y", String.valueOf(n6), "z", String.valueOf(n7))));
                return;
            }
            List list3 = string4.equals("left") ? list2.stream().filter(actionLog -> "BLOCK_PLACE".equalsIgnoreCase(actionLog.getAction()) || "BLOCK_BREAK".equalsIgnoreCase(actionLog.getAction())).collect(Collectors.toList()) : (string4.equals("right") ? (bl ? list2.stream().filter(actionLog -> "CONTAINER_ADD".equalsIgnoreCase(actionLog.getAction()) || "CONTAINER_TAKE".equalsIgnoreCase(actionLog.getAction()) || "HOPPER_TRANSFER".equalsIgnoreCase(actionLog.getAction()) || "INTERACT".equalsIgnoreCase(actionLog.getAction())).collect(Collectors.toList()) : list2.stream().filter(actionLog -> !"BLOCK_PLACE".equalsIgnoreCase(actionLog.getAction()) && !"BLOCK_BREAK".equalsIgnoreCase(actionLog.getAction())).collect(Collectors.toList())) : list2);
            if (list3.isEmpty()) {
                player.sendMessage(this.plugin.getLang().get("messages.inspect.noLogsForMode", Map.of("mode", string4)));
                return;
            }
            int n5 = (int)Math.ceil((double)list3.size() / 7.0);
            if (n8 < 1 || n8 > n5) {
                player.sendMessage(this.plugin.getLang().get("messages.inspect.invalidPage", Map.of("total", String.valueOf(n5))));
                return;
            }
            int n6 = (n8 - 1) * 7;
            int n7 = Math.min(n6 + 7, list3.size());
            List list4 = list3.subList(n6, n7);
            TextComponent textComponent = this.serializer.deserialize(this.plugin.getLang().get("messages.inspect.header", Map.of("x", String.valueOf(n), "y", String.valueOf(n6), "z", String.valueOf(n7))));
            Set set = list4.stream().map(ActionLog::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            this.databaseMariadb.fetchRolledBackFlags(set, arg_0 -> this.lambda$execute$6(list4, n, n6, n7, n8, n5, string4, (Component)textComponent, player, arg_0));
        });
        return true;
    }

    private Component buildInspectorFooter(int n, int n2, int n3, int n4, int n5, String string) {
        TextComponent textComponent;
        TextComponent textComponent2 = Component.empty();
        if (n4 > 1) {
            textComponent = ((TextComponent)Component.text((String)this.plugin.getLang().get("messages.inspect.prevArrow", Map.of())).color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)("/po inspect " + string + " x:" + n + " y:" + n2 + " z:" + n3 + " page:" + (n4 - 1))));
            textComponent2 = textComponent2.append((Component)textComponent).append((Component)Component.space());
        }
        textComponent = this.serializer.deserialize(this.plugin.getLang().get("messages.inspect.pageInfo", Map.of("current", String.valueOf(n4), "total", String.valueOf(n5))));
        textComponent2 = textComponent2.append((Component)textComponent).append((Component)Component.space());
        if (n4 < n5) {
            Component component = ((TextComponent)Component.text((String)this.plugin.getLang().get("messages.inspect.nextArrow", Map.of())).color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)("/po inspect " + string + " x:" + n + " y:" + n2 + " z:" + n3 + " page:" + (n4 + 1))));
            textComponent2 = textComponent2.append(component);
        }
        return textComponent2;
    }

    private static boolean isWeLog(ActionLog actionLog) {
        try {
            String string;
            String string2 = actionLog.getMetadata();
            if (string2 != null) {
                string = string2.toLowerCase(Locale.ROOT);
                if (string.contains("\"we\":true")) {
                    return true;
                }
                if (string.contains("\"worldedit\"")) {
                    return true;
                }
                if (string.contains("\"fawe\"")) {
                    return true;
                }
                if (string.contains("\"source\":\"we\"") || string.contains("\"source\":\"worldedit\"")) {
                    return true;
                }
            }
            if ((string = actionLog.getDetails()) != null) {
                String string3 = string.toLowerCase(Locale.ROOT);
                if (string3.contains("worldedit")) {
                    return true;
                }
                if (string3.contains("fawe")) {
                    return true;
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        return Collections.emptyList();
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        return this.tabComplete(commandSender, command, string, stringArray);
    }

    private /* synthetic */ void lambda$execute$6(List list, int n, int n2, int n3, int n4, int n5, String string, Component component, Player player, Set set) {
        ActionLog actionLog2;
        TextComponent textComponent = Component.empty();
        for (ActionLog actionLog2 : list) {
            String string2 = actionLog2.getPlayerName() != null ? actionLog2.getPlayerName() : Optional.ofNullable(Bukkit.getOfflinePlayer((UUID)actionLog2.getPlayerUUID()).getName()).orElse("unknown");
            Component component2 = PoCommand.formatLogComponent(actionLog2, string2, false);
            if (set.contains(actionLog2.getId())) {
                component2 = component2.decoration(TextDecoration.STRIKETHROUGH, true);
            }
            textComponent = textComponent.append(component2).append((Component)Component.newline());
        }
        Component component3 = this.buildInspectorFooter(n, n2, n3, n4, n5, string);
        actionLog2 = component.append((Component)textComponent).append(component3);
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> InspectSubCommand.lambda$execute$5(player, (Component)actionLog2));
    }

    private static /* synthetic */ void lambda$execute$5(Player player, Component component) {
        player.sendMessage(component);
    }
}

