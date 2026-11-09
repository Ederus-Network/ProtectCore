/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.event.ClickEvent
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 */
package com.lucidaps.web;

import com.lucidaps.ProtectCore;
import com.lucidaps.utilities.TimeUtil;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class EditorCommand
implements CommandExecutor,
TabCompleter {
    private final ProtectCore plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public EditorCommand(ProtectCore protectCore) {
        this.plugin = protectCore;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        int n;
        String string2 = null;
        String string3 = "";
        for (n = 1; n < stringArray.length; ++n) {
            if (stringArray[n].toLowerCase().startsWith("user:")) {
                string2 = stringArray[n].substring(5);
                continue;
            }
            if (!stringArray[n].toLowerCase().startsWith("time:")) continue;
            string3 = stringArray[n].substring(5);
        }
        n = string2 == null || string2.isEmpty() ? 1 : 0;
        long l = 0L;
        if (!string3.isEmpty()) {
            long l2 = TimeUtil.parseTimePeriod(string3);
            if (l2 < 0L) {
                String string4 = this.plugin.getConfig().getString("messages.editor.invalidTime");
                String string5 = ChatColor.translateAlternateColorCodes((char)'&', (String)string4.replace("{time}", string3));
                commandSender.sendMessage(string5);
                return true;
            }
            l = (System.currentTimeMillis() - l2) / 1000L;
        }
        if (n != 0) {
            this.uploadAndSend(commandSender, l, null);
            return true;
        }
        this.uploadAndSend(commandSender, l, string2);
        return true;
    }

    private void uploadAndSend(CommandSender commandSender, long l, String string) {
        if (!this.plugin.getConfig().getBoolean("editor.enabled", true)) {
            commandSender.sendMessage(String.valueOf(ChatColor.RED) + "The embedded editor is disabled.");
            return;
        }
        if (this.plugin.getApiServer() == null) {
            try {
                this.plugin.initApiServer();
                this.plugin.startTokenGc();
            }
            catch (IOException iOException) {
                commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Editor API server is not available (" + iOException.getMessage() + ").");
                this.plugin.getLogger().severe("Cannot start API server on demand: " + String.valueOf(iOException));
                return;
            }
        }
        if (this.plugin.getApiServer() == null) {
            commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Editor API server is not available.");
            return;
        }
        String string2 = this.plugin.getApiServer().issueAccessToken();
        String string3 = this.plugin.getConfig().getString("editor.publicBaseUrl", "http://localhost:9123");
        while (string3.endsWith("/")) {
            string3 = string3.substring(0, string3.length() - 1);
        }
        StringBuilder stringBuilder = new StringBuilder().append(string3).append("/editor?token=").append(string2).append("&since=").append(l);
        if (string != null && !string.isEmpty()) {
            stringBuilder.append("&user=").append(URLEncoder.encode(string, StandardCharsets.UTF_8));
        }
        String string4 = stringBuilder.toString();
        if (commandSender instanceof Player) {
            String string5 = this.plugin.getConfig().getString("messages.editor.clickPrompt");
            Component component = this.legacy.deserialize(string5).clickEvent(ClickEvent.openUrl((String)string4));
            commandSender.sendMessage(component);
        } else {
            String string6 = this.plugin.getConfig().getString("messages.editor.consoleOpen");
            String string7 = ChatColor.translateAlternateColorCodes((char)'&', (String)string6.replace("{url}", string4));
            commandSender.sendMessage(string7);
        }
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (stringArray.length < 2) {
            return Collections.emptyList();
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        String string2 = stringArray[stringArray.length - 1].toLowerCase(Locale.ROOT);
        boolean bl = false;
        boolean bl2 = false;
        for (int i = 1; i < stringArray.length; ++i) {
            String string3 = stringArray[i].toLowerCase(Locale.ROOT);
            if (string3.startsWith("user:")) {
                bl = true;
            }
            if (!string3.startsWith("time:")) continue;
            bl2 = true;
        }
        if (!bl && "user:".startsWith(string2)) {
            arrayList.add("user:");
        }
        if (!bl2 && "time:".startsWith(string2)) {
            arrayList.add("time:");
        }
        if (string2.startsWith("user:")) {
            String string4 = string2.substring("user:".length());
            for (Player object : Bukkit.getOnlinePlayers()) {
                String string3 = object.getName();
                if (!string3.toLowerCase(Locale.ROOT).startsWith(string4)) continue;
                arrayList.add("user:" + string3);
            }
        }
        if (string2.startsWith("time:")) {
            List<String> list = List.of("time:7d", "time:3d", "time:1d", "time:12h", "time:6h", "time:1h", "time:30m", "time:15m", "time:5m");
            for (String string4 : list) {
                if (!string4.startsWith(string2)) continue;
                arrayList.add(string4);
            }
        }
        return arrayList;
    }
}

