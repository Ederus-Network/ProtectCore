/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.event.ClickEvent
 *  net.kyori.adventure.text.event.HoverEvent
 *  net.kyori.adventure.text.event.HoverEventSource
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.World
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 */
package com.lucidaps.commands;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lucidaps.ProtectCore;
import com.lucidaps.commands.PcTpCommand;
import com.lucidaps.commands.SubCommand;
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
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class PoCommand
implements CommandExecutor,
TabCompleter {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<String, SubCommand>();
    private static String teleportHoverMessage;
    private static final UUID ZERO_UUID;

    public PoCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb, DbPurger dbPurger) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
        this.registerSubCommand(new LookupSubCommand(protectCore, databaseMariadb));
        this.registerSubCommand(new NearSubCommand(protectCore, databaseMariadb));
        this.registerSubCommand(new EditorSubCommand(protectCore, databaseMariadb));
        this.registerSubCommand(new RollbackSubCommand(protectCore, databaseMariadb));
        this.registerSubCommand(new ReloadSubCommand(protectCore));
        InspectSubCommand inspectSubCommand = new InspectSubCommand(protectCore, databaseMariadb);
        this.registerSubCommand(inspectSubCommand);
        this.subCommands.put("i", inspectSubCommand);
        this.registerSubCommand(new PurgeSubCommand(protectCore, dbPurger));
        this.registerSubCommand(new StatusSubCommand(protectCore, databaseMariadb, dbPurger));
        teleportHoverMessage = protectCore.getLang().get("messages.teleportHover", Map.of());
    }

    private void registerSubCommand(SubCommand subCommand) {
        this.subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("protectcore.commands")) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        if (stringArray.length < 1) {
            String string2 = String.join((CharSequence)"|", this.subCommands.keySet());
            commandSender.sendMessage(this.plugin.getLang().get("messages.usageGeneric", Map.of("subcommands", string2)));
            return true;
        }
        SubCommand subCommand = this.subCommands.get(stringArray[0].toLowerCase());
        if (subCommand == null) {
            String string3 = String.join((CharSequence)", ", this.subCommands.keySet());
            commandSender.sendMessage(this.plugin.getLang().get("messages.unknownSubcommand", Map.of("subcommands", string3)));
            return true;
        }
        return subCommand.execute(commandSender, command, string, stringArray);
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string2, String[] stringArray) {
        if (stringArray.length == 1) {
            return this.subCommands.keySet().stream().filter(string -> string.startsWith(stringArray[0].toLowerCase())).collect(Collectors.toList());
        }
        SubCommand subCommand = this.subCommands.get(stringArray[0].toLowerCase());
        if (subCommand != null) {
            return subCommand.tabComplete(commandSender, command, string2, stringArray);
        }
        return Collections.emptyList();
    }

    private static Component applyMetadataHover(Component component, ActionLog actionLog) {
        if (actionLog.getMetadata() != null && !actionLog.getMetadata().isEmpty()) {
            String string = PoCommand.formatMetadata(actionLog.getMetadata());
            TextComponent textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(string);
            return component.hoverEvent((HoverEventSource)HoverEvent.showText((Component)textComponent));
        }
        return component;
    }

    public static Component formatLogComponent(ActionLog actionLog, String string, boolean bl) {
        LogFormatting.FormattedLogParts formattedLogParts;
        String[] stringArray;
        String string2 = PoCommand.formatTimeAgo(actionLog.getTimestamp());
        String string3 = actionLog.getDetails();
        String string4 = string3 == null ? "" : string3.toLowerCase(Locale.ROOT);
        boolean bl2 = ZERO_UUID.equals(actionLog.getPlayerUUID());
        if (bl2) {
            if (actionLog.getAction().equalsIgnoreCase("ENTITY_DEATH") && string4.contains(" by ")) {
                stringArray = string4.split(" by ");
                if (stringArray.length == 2) {
                    String string5 = stringArray[1].trim();
                    String string6 = stringArray[0].trim();
                    String string7 = "&c-";
                    String string8 = "killed";
                    String string9 = "&7" + string2 + " ago " + string7 + " &d" + string5 + " &f" + string8 + " &d" + string6 + "&f.";
                    Component component = bl ? PoCommand.buildFinalComponent(string9, actionLog) : LegacyComponentSerializer.legacyAmpersand().deserialize(string9);
                    return PoCommand.applyMetadataHover(component, actionLog);
                }
            } else if (actionLog.getAction().equalsIgnoreCase("HOPPER_TRANSFER")) {
                String[] stringArray2 = string4.split(" ");
                String string10 = "&e>";
                String string11 = "transfer";
                String string12 = stringArray2.length >= 3 ? "&d" + stringArray2[0] + " &f" + string11 + " &d" + stringArray2[1] + " " + stringArray2[2] : (stringArray2.length >= 2 ? "&d" + stringArray2[0] + " &f" + string11 + " &d" + stringArray2[1] : "&d" + string4);
                String string13 = "&7" + string2 + " ago " + string10 + " " + string12 + "&f.";
                Component component = bl ? PoCommand.buildFinalComponent(string13, actionLog) : LegacyComponentSerializer.legacyAmpersand().deserialize(string13);
                return PoCommand.applyMetadataHover(component, actionLog);
            }
        } else if (actionLog.getAction().equalsIgnoreCase("BLOCK_BURN")) {
            String string14 = string4.replace("block ", "").replace(" is burning", "").replace(" burned", "").trim();
            String string15 = "&c-";
            String string16 = "burned";
            String string17 = "&7" + string2 + " ago " + string15 + " &dfire &f" + string16 + " &d" + string14 + "&f.";
            Component component = bl ? PoCommand.buildFinalComponent(string17, actionLog) : LegacyComponentSerializer.legacyAmpersand().deserialize(string17);
            return PoCommand.applyMetadataHover(component, actionLog);
        }
        if ((formattedLogParts = (stringArray = LogFormatting.LogFormatter.getFormatter(actionLog.getAction())).format(actionLog, string4, string2, bl)).overrideComponent() != null) {
            return PoCommand.applyMetadataHover(formattedLogParts.overrideComponent(), actionLog);
        }
        String string18 = formattedLogParts.symbol();
        String string19 = formattedLogParts.actionVerb();
        String string20 = formattedLogParts.detailSuffix();
        String string21 = formattedLogParts.usernameOverride() != null ? formattedLogParts.usernameOverride() : string;
        String string22 = string21 == null || string21.isBlank() ? "unknown" : string21;
        String string23 = formattedLogParts.detailContainsVerb() ? "&7" + string2 + " ago " + string18 + " &d" + string22 + " " + string20 + "&f." : "&7" + string2 + " ago " + string18 + " &d" + string22 + " &f" + string19 + " " + string20 + "&f.";
        Component component = bl ? PoCommand.buildFinalComponent(string23, actionLog) : LegacyComponentSerializer.legacyAmpersand().deserialize(string23);
        return PoCommand.applyMetadataHover(component, actionLog);
    }

    public static Component formatLogComponent(ActionLog actionLog, String string) {
        return PoCommand.formatLogComponent(actionLog, string, true);
    }

    public static void updateTeleportHover(Lang lang) {
        teleportHoverMessage = lang.get("messages.teleportHover", Map.of());
    }

    private static Component buildFinalComponent(String string, ActionLog actionLog) {
        Object object;
        TextComponent textComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(string);
        String string2 = "(x" + actionLog.getX() + "/y" + actionLog.getY() + "/z" + actionLog.getZ() + "/" + actionLog.getWorld() + ")";
        World world2 = Bukkit.getWorld((String)actionLog.getWorld());
        if (world2 == null && ((Optional)(object = Bukkit.getWorlds().stream().filter(world -> world.getName().equalsIgnoreCase(actionLog.getWorld()) || world.getKey().toString().equalsIgnoreCase(actionLog.getWorld())).findFirst())).isPresent()) {
            world2 = (World)((Optional)object).get();
        }
        object = world2 != null ? world2.getKey().toString() : actionLog.getWorld();
        String string3 = UUID.randomUUID().toString();
        PcTpCommand.registerToken(string3);
        String string4 = "/pc_tp " + string3 + " " + (String)object + " " + actionLog.getX() + " " + actionLog.getY() + " " + actionLog.getZ();
        Component component = ((TextComponent)((TextComponent)Component.text((String)string2).color((TextColor)NamedTextColor.GRAY)).hoverEvent((HoverEventSource)HoverEvent.showText((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(teleportHoverMessage != null ? teleportHoverMessage : "Click to teleport")))).clickEvent(ClickEvent.runCommand((String)string4));
        String string5 = "         &7^ ";
        Component component2 = LegacyComponentSerializer.legacyAmpersand().deserialize(string5).append(component);
        return textComponent.append((Component)Component.newline()).append(component2);
    }

    private static String formatMetadata(String string) {
        try {
            JsonObject jsonObject = (JsonObject)new Gson().fromJson(string, JsonObject.class);
            if (jsonObject.has("item") && jsonObject.get("item").isJsonObject()) {
                jsonObject = jsonObject.getAsJsonObject("item");
            }
            StringBuilder stringBuilder = new StringBuilder();
            if (jsonObject.has("displayName")) {
                stringBuilder.append("\u00a75Name: \u00a7f").append(jsonObject.get("displayName").getAsString()).append('\n');
            }
            if (jsonObject.has("enchantments")) {
                stringBuilder.append("\u00a75Enchantments:\n");
                jsonObject.getAsJsonObject("enchantments").entrySet().forEach(entry -> stringBuilder.append("  \u00a77- ").append((String)entry.getKey()).append(": \u00a7f").append(((JsonElement)entry.getValue()).getAsInt()).append('\n'));
            }
            if (jsonObject.has("lore")) {
                stringBuilder.append("\u00a75Lore:\n");
                jsonObject.getAsJsonArray("lore").forEach(jsonElement -> stringBuilder.append("  \u00a77- ").append(jsonElement.getAsString()).append('\n'));
            }
            if (stringBuilder.length() > 0 && stringBuilder.charAt(stringBuilder.length() - 1) == '\n') {
                stringBuilder.setLength(stringBuilder.length() - 1);
            }
            return stringBuilder.toString();
        }
        catch (Exception exception) {
            return string;
        }
    }

    private static String formatTimeAgo(Timestamp timestamp) {
        long l = System.currentTimeMillis() - timestamp.getTime();
        long l2 = l / 1000L;
        if (l2 < 0L) {
            l2 = 0L;
        }
        if (l2 >= 86400L) {
            return String.format("%.2fd", (double)l2 / 86400.0);
        }
        if (l2 >= 3600L) {
            return String.format("%.2fh", (double)l2 / 3600.0);
        }
        if (l2 >= 60L) {
            return String.format("%.2fm", (double)l2 / 60.0);
        }
        return String.format("%.2fs", (double)l / 1000.0);
    }

    public static Component formatFooterComponent(String string, String string2, int n, int n2) {
        return PoCommand.formatFooterComponent(string, string2, n, n2, "");
    }

    public static Component formatFooterComponent(String string, String string2, int n, int n2, String string3) {
        TextComponent textComponent;
        LegacyComponentSerializer legacyComponentSerializer = LegacyComponentSerializer.legacyAmpersand();
        TextComponent textComponent2 = Component.empty();
        String string4 = string3 != null && !string3.isBlank() ? " " + string3.trim() : "";
        String string5 = "/po lookup" + (String)(string != null && !string.isBlank() ? " user:" + string : "") + " time:" + string2 + string4 + " page:";
        if (n > 1) {
            textComponent = ((TextComponent)Component.text((String)"\u25c0").color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)(string5 + (n - 1))));
            textComponent2 = textComponent2.append((Component)textComponent).append((Component)Component.space());
        }
        textComponent = legacyComponentSerializer.deserialize("&dPage &f" + n + "/" + n2);
        textComponent2 = textComponent2.append((Component)textComponent);
        if (n < n2) {
            textComponent2 = textComponent2.append((Component)Component.space());
            Component component = ((TextComponent)Component.text((String)"\u25b6").color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)(string5 + (n + 1))));
            textComponent2 = textComponent2.append(component);
        }
        return textComponent2;
    }

    public static Component formatFooterComponentWindowed(String string, String string2, int n, int n2, int n3, boolean bl, boolean bl2, String string3) {
        Component component;
        LegacyComponentSerializer legacyComponentSerializer = LegacyComponentSerializer.legacyAmpersand();
        TextComponent textComponent = Component.empty();
        String string4 = string3 != null && !string3.isBlank() ? " " + string3.trim() : "";
        String string5 = "/po lookup" + (String)(string != null && !string.isBlank() ? " user:" + string : "") + " time:" + string2 + string4 + " page:";
        if (bl) {
            int n4 = Math.max(1, n2 - 1);
            component = ((TextComponent)((TextComponent)Component.text((String)"\u23ee").color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)(string5 + n4)))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)legacyComponentSerializer.deserialize("&7Jump to pages " + Math.max(1, n2 - 10) + "\u2013" + (n2 - 1))));
            textComponent = textComponent.append(component).append((Component)Component.space());
        }
        if (n > n2) {
            Component component2 = ((TextComponent)Component.text((String)"\u25c0").color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)(string5 + (n - 1))));
            textComponent = textComponent.append(component2).append((Component)Component.space());
        }
        TextComponent textComponent2 = legacyComponentSerializer.deserialize("&dPage &f" + n + " &7(" + n2 + "\u2013" + n3 + ")");
        textComponent = textComponent.append((Component)textComponent2);
        if (n < n3) {
            textComponent = textComponent.append((Component)Component.space());
            component = ((TextComponent)Component.text((String)"\u25b6").color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)(string5 + (n + 1))));
            textComponent = textComponent.append(component);
        }
        if (bl2) {
            textComponent = textComponent.append((Component)Component.space());
            int n5 = n3 + 1;
            Component component3 = ((TextComponent)((TextComponent)Component.text((String)"\u23ed").color((TextColor)NamedTextColor.DARK_AQUA)).clickEvent(ClickEvent.runCommand((String)(string5 + n5)))).hoverEvent((HoverEventSource)HoverEvent.showText((Component)legacyComponentSerializer.deserialize("&7Jump to pages " + (n3 + 1) + "\u2013" + (n3 + 10))));
            textComponent = textComponent.append(component3);
        }
        return textComponent;
    }

    static {
        ZERO_UUID = new UUID(0L, 0L);
    }
}

