/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 */
package com.lucidaps.commands.subcommands;

import com.lucidaps.ProtectCore;
import com.lucidaps.commands.PoCommand;
import com.lucidaps.commands.SubCommand;
import com.lucidaps.utilities.Lang;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class ReloadSubCommand
implements SubCommand {
    private final ProtectCore plugin;

    public ReloadSubCommand(ProtectCore protectCore) {
        this.plugin = protectCore;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public boolean execute(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("protectcore.reload")) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        try {
            this.plugin.updateConfigWithMissingDefaults();
            this.plugin.reloadConfig();
            this.plugin.reloadWorldFilters();
            this.plugin.loadLoggedActionsConfig();
            this.plugin.reloadRuntimeConfiguration();
            this.plugin.setLang(new Lang(this.plugin));
            PoCommand.updateTeleportHover(this.plugin.getLang());
            if (this.plugin.getApiServer() != null) {
                this.plugin.getApiServer().stop();
            }
            this.plugin.initApiServer();
            this.plugin.startTokenGc();
            this.plugin.reloadWorldEditBridge();
            commandSender.sendMessage(this.plugin.getLang().get("messages.reload.success", Map.of()));
        }
        catch (Exception exception) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.reload.error", Map.of()));
            this.plugin.getLogger().log(Level.SEVERE, "Reload error", exception);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        return Collections.emptyList();
    }
}

