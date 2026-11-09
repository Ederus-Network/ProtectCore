/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 */
package com.lucidaps.commands.subcommands;

import com.lucidaps.ProtectCore;
import com.lucidaps.commands.SubCommand;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.db.DbPurger;
import com.lucidaps.db.PurgeStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public final class StatusSubCommand
implements SubCommand {
    private final ProtectCore plugin;
    private final DatabaseMariadb database;
    private final DbPurger purger;

    public StatusSubCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb, DbPurger dbPurger) {
        this.plugin = protectCore;
        this.database = databaseMariadb;
        this.purger = dbPurger;
    }

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public boolean execute(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("protectcore.admin.status")) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        commandSender.sendMessage("\u00a7dProtectCore \u00a7foperational status");
        commandSender.sendMessage("\u00a77Database: \u00a7f" + String.valueOf((Object)this.database.getHealthState()));
        commandSender.sendMessage("\u00a77Action queue: \u00a7f" + this.database.getQueuedActionCount() + "\u00a77/\u00a7f" + this.database.getMaxQueuedActions() + " \u00a77| generic: \u00a7f" + this.database.getQueuedGenericTaskCount());
        if (this.database.getLastConnectedAtMillis() > 0L) {
            commandSender.sendMessage("\u00a77Last connected: \u00a7f" + String.valueOf(Instant.ofEpochMilli(this.database.getLastConnectedAtMillis())));
        }
        if (!this.database.getLastConnectionError().isBlank()) {
            commandSender.sendMessage("\u00a7cLast database error: " + this.database.getLastConnectionError());
        }
        PurgeStatus purgeStatus = this.purger.getStatus();
        commandSender.sendMessage("\u00a77Purge: \u00a7f" + String.valueOf((Object)purgeStatus.stage) + (purgeStatus.running ? " \u00a7e(running)" : ""));
        commandSender.sendMessage("\u00a77Editor: \u00a7f" + (this.plugin.getApiServer() == null ? "STOPPED" : "RUNNING"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        return List.of();
    }
}

